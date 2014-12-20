package com.gtan.repox

import java.nio.file.Paths

import akka.actor._
import com.gtan.repox.config.Config
import io.undertow.Handlers
import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.resource.{FileResourceManager, ResourceManager}
import io.undertow.util.StatusCodes

import scala.collection.Set
import scala.util.Random

case class Queue(method: Symbol, uri: String)

object RequestQueueMaster {

  // send by ConfigPersister
  case object ConfigLoaded

  // send when mainClient and proxyClients are all initialized
  case object ClientsInitialized

  // send by QueueWorker
  case class Dead(queue: Queue)

  // send by FileDeleter
  case class Quarantine(uri: String)

  case class FileDeleted(uri: String)

}

class RequestQueueMaster extends Actor with Stash with ActorLogging {

  import RequestQueueMaster._
  import concurrent.ExecutionContext.Implicits.global

  var children    = Map.empty[Queue, ActorRef]
  // Quuee -> Get/HeadQueueWorker
  var quarantined = Map.empty[String, ActorRef] // Uri -> FileDeleter

  override def receive = waitingConfigRecover

  def waitingConfigRecover: Receive = {
    case ConfigLoaded =>
      log.debug(s"Config loaded.")
      val fut1 = Repox.clients.alter(Config.connectors.map(
        connector => connector.name -> connector.createClient
      ).toMap)
      log.debug(s"storage: ${Config.storagePath}, resourceBases: ${Config.resourceBases}")
      val storage = Repox.storageManager -> Handlers.resource(Repox.storageManager)
      val extra = for (rb <- Config.resourceBases) yield {
        val resourceManager: ResourceManager = new FileResourceManager(Paths.get(rb).toFile, 100 * 1024)
        val resourceHandler = Handlers.resource(resourceManager)
        resourceManager -> resourceHandler
      }
      val fut2 = Repox.resourceHandlers.alter((extra :+ storage).toMap)
      (fut1 zip fut2).onSuccess { case _ => self ! ClientsInitialized}
    case ClientsInitialized =>
      log.debug(s"AHC clients (${Repox.clients.get().keys.mkString(",")}}) initialized.")
      log.debug(s"ResourceBases (${Repox.resourceHandlers.get().keys.map(base => Option(base.getResource("/")).map(_.getResourceManagerRoot).getOrElse("Invalid Base")).mkString(",")}) initialized.")
      unstashAll()
      context become started
    case msg =>
      log.debug("Config loading , stash all msgs...")
      stash()
  }

  def started: Receive = {
    case Quarantine(uri) =>
      quarantined.get(uri) match {
        case None =>
          quarantined = quarantined.updated(uri, sender())
          quarantined = quarantined.updated(uri + ".sha1", sender())
          sender ! FileDeleter.Quarantined
        case _ => // already quarantined , ignore
      }
    case FileDeleted(uri) =>
      quarantined = quarantined - uri - (uri + ".sha1")
      log.debug(s"Redownloading $uri(and ${uri + ".sha1"})")
      self ! Requests.Download(uri, Config.enabledRepos)
    case Dead(queue) =>
      for (worker <- children.get(queue)) {
        log.debug(s"RequestQueueMaster stopping worker ${worker.path.name}")
        worker ! PoisonPill
      }
      children = children - queue

    case req@Requests.Download(uri, from) =>
      val queue = Queue('get, uri)
      Repox.downloaded(uri) match {
        case Some(Tuple2(resourceManager, resourceHandler)) =>
          for (worker <- children.get(queue)) {
            worker ! PoisonPill
          }
        case None =>
          children.get(queue) match {
            case None =>
              val childName = s"DownloadQueueWorker_${Random.nextInt()}_from_${from.map(_.name).mkString("_")}"
              val worker = context.actorOf(Props(classOf[GetQueueWorker], uri), name = childName)
              children = children.updated(queue, worker)
              worker ! req
            case _ => // downloading proceeding, ignore this one
          }
      }
    case req@Requests.Get(exchange) =>
      val uri = exchange.getRequestURI
      log.debug(s"uri=$uri")
      quarantined.get(uri) match {
        case None =>
          val queue = Queue('get, uri)
          Repox.downloaded(uri) match {
            case Some(Tuple2(resourceManager, resourceHandler)) =>
              Repox.immediateFile(resourceHandler, exchange)
              for (worker <- children.get(queue)) {
                worker ! PoisonPill
              }
            case None =>
              children.get(queue) match {
                case None =>
                  val childName = s"GetQueueWorker_${Random.nextInt()}"
                  val worker = context.actorOf(Props(classOf[GetQueueWorker], uri), name = childName)
                  children = children.updated(Queue('get, uri), worker)
                  worker ! req
                case Some(worker) =>
                  worker ! req
              }
          }
        case Some(deleter) =>
          // file quarantined, 404
          Repox.respond404(exchange)
      }
    case req@Requests.Head(exchange) =>
      val uri = exchange.getRequestURI
      val queue = Queue('head, uri)
      if (Config.immediate404Rules.filterNot(_.disabled).exists(_.matches(uri))) {
        Repox.immediate404(exchange)
        for (worker <- children.get(queue)) {
          worker ! PoisonPill
        }
      } else {
        Repox.peer(uri).find(p => Repox.downloaded(p).isDefined) match {
          case Some(peer) =>
            Repox.smart404(exchange)
          case _ =>
            quarantined.get(uri) match {
              case None =>
                Repox.downloaded(uri) match {
                  case Some(Tuple2(resourceManager, resourceHandler)) =>
                    Repox.immediateHead(resourceManager, exchange)
                    for (worker <- children.get(queue)) {
                      worker ! PoisonPill
                    }
                  case None =>
                    children.get(queue) match {
                      case None =>
                        val childName = s"HeadQueueWorker_${Random.nextInt()}"
                        val worker = context.actorOf(Props(classOf[HeadQueueWorker], uri), name = childName)
                        log.debug(s"create HeadQueueWorker $childName")
                        children = children.updated(queue, worker)
                        worker ! req
                      case Some(worker) =>
                        log.debug(s"Enqueue to HeadQueueWorker ${worker.path.name} $uri")
                        worker ! req
                    }
                }
              case Some(deleter) =>
                // file quarantined, 404
                Repox.respond404(exchange)
            }
        }
      }
  }
}
