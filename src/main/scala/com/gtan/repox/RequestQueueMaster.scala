package com.gtan.repox

import akka.actor._
import com.gtan.repox.config.{Config, ConfigFormats}

case class Queue(method: Symbol, uri: String)

object RequestQueueMaster {

  // send by ConfigPersister
  case object ConfigLoaded

  // send when mainClient and proxyClients are all initialized
  case object ClientsInitialized

  // send by QueueWorker
  case class KillMe(queue: Queue)

  // send by FileDeleter
  case class Quarantine(uri: String)

  case class FileDeleted(uri: String)

}

class RequestQueueMaster extends Actor with Stash with ActorLogging with ConfigFormats {

  import RequestQueueMaster._

  import concurrent.ExecutionContext.Implicits.global

  var children = Map.empty[Queue, ActorRef] // Queue -> GetHeadQueueWorker

  var quarantined = Map.empty[String, ActorRef] // Uri -> FileDeleter

  override def receive = initializing

  def initializing: Receive = {
    case ConfigLoaded =>
      log.info("Config loaded.")
      val fut1 = Repox.initClients()
      val fut2 = Repox.initResourceManagers()
      for (both <- fut1 zip fut2) {
        self ! ClientsInitialized
      }
    case ClientsInitialized =>
      log.debug(s"ResourceBases (${Repox.resourceHandlers.get().keys.map(_.getBase).mkString(",")}) initialized.")
      log.debug(s"AHC clients (${Repox.clients.get().keys.mkString(",")}) initialized.")
      unstashAll()
      context become started
    case msg =>
      log.debug("Repox initializing , stash all msgs...")
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
      quarantined = quarantined - uri - s"$uri.sha1"
    //      log.debug(s"Redownloading $uri and $uri.sha1")
    //      self ! Requests.Download(uri, Config.enabledRepos)

    case KillMe(queue) =>
      for (worker <- children.get(queue)) {
        log.debug(s"RequestQueueMaster stopping worker ${worker.path.name}")
        worker ! PoisonPill
      }
      children = children - queue

    case req@Requests.Get(exchange) =>
      val uri = exchange.getRequestURI
      quarantined.get(uri) match {
        case None =>
          val queue = Queue('get, uri)
          if (Config.immediate404Rules.filterNot(_.disabled).exists(_.matches(uri))) {
            Repox.immediate404(exchange)
            for (worker <- children.get(queue)) {
              worker ! PoisonPill
            }
          } else {
            for (peers <- Repox.peer(uri)) {
              peers.find(p => Repox.downloaded(p).isDefined) match {
                case Some(peer) =>
                  Repox.smart404(exchange)
                case None =>
                  Repox.downloaded(uri) match {
                    case Some(Tuple2(resourceManager, resourceHandler)) =>
                      Repox.immediateFile(resourceHandler, exchange)
                      for (worker <- children.get(queue)) {
                        worker ! PoisonPill
                      }
                    case None =>
                      children.get(queue) match {
                        case None =>
                          val childName = s"GetQueueWorker_${Repox.nextId}"
                          val worker = context.actorOf(Props(classOf[GetQueueWorker], uri), name = childName)
                          children = children.updated(Queue('get, uri), worker)
                          worker ! req
                        case Some(worker) =>
                          worker ! req
                      }
                  }
              }
            }
          }
        case Some(deleter) =>
          // file quarantined, 404
          Repox.respond404(exchange)
      }
    case Requests.Get4s(request) =>
      import org.http4s.dsl.{uri => _, _}
      val uri = request.uri.toString
      quarantined.get(uri) match {
        case None =>
          val queue = Queue('get, uri)
          if (Config.immediate404Rules.filterNot(_.disabled).exists(_.matches(uri))) {
            Repox.immediate4044s(sender, uri)
            for (worker <- children.get(queue)) {
              worker ! PoisonPill
            }
          } else {
            for (peers <- Repox.peer(uri)) {
              peers.find(p => Repox.downloaded4s(p).isDefined) match {
                case Some(peer) =>
                  Repox.smart4044s(sender, uri)
                case None =>
                  Repox.downloaded4s(uri) match {
                    case Some(file) =>
                      Repox.immediateFile4s(sender, request, file)
                      for (worker <- children.get(queue)) {
                        worker ! PoisonPill
                      }
                    case None =>
                      children.get(queue) match {
                        case None =>
                          val childName = s"GetQueueWorker_${Repox.nextId}"
                          val worker = context.actorOf(Props(classOf[GetQueueWorker], uri), name = childName)
                          children = children.updated(Queue('get, uri), worker)
                          worker ! Requests.Get4s(request)
                        case Some(worker) =>
                          worker ! Requests.Get4s(request)
                      }
                  }
              }
            }
          }
        case Some(deleter) =>
          // file quarantined, 404
          sender() ! NotFound()
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
        for (peers <- Repox.peer(uri)) {
          peers.find(p => Repox.downloaded(p).isDefined) match {
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
                          val workerActorName = s"HeadQueueWorker_${Repox.nextId}"
                          val worker = context.actorOf(Props(classOf[HeadQueueWorker], uri), workerActorName)
                          log.debug(s"create HeadQueueWorker $workerActorName")
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
    case Requests.Head4s(request) =>
      import org.http4s.dsl.{uri => _, _}
      val uri = request.uri.toString()
      val queue = Queue('head, uri)
      if (Config.immediate404Rules.filterNot(_.disabled).exists(_.matches(uri))) {
        Repox.immediate4044s(sender(), uri)
        for (worker <- children.get(queue)) {
          worker ! PoisonPill
        }
      } else {
        for (peers <- Repox.peer(uri)) {
          peers.find(p => Repox.downloaded4s(p).isDefined) match {
            case Some(peer) =>
              Repox.smart4044s(sender(), uri)
            case _ =>
              quarantined.get(uri) match {
                case None =>
                  Repox.downloaded4s(uri) match {
                    case Some(file) =>
                      Repox.immediateHead4s(sender(), file, request)
                      for (worker <- children.get(queue)) {
                        worker ! PoisonPill
                      }
                    case None =>
                      children.get(queue) match {
                        case None =>
                          val workerActorName = s"HeadQueueWorker_${Repox.nextId}"
                          val worker = context.actorOf(Props(classOf[HeadQueueWorker], uri), workerActorName)
                          log.debug(s"create HeadQueueWorker $workerActorName")
                          children = children.updated(queue, worker)
                          worker ! Requests.Head4s(request)
                        case Some(worker) =>
                          log.debug(s"Enqueue to HeadQueueWorker ${worker.path.name} $uri")
                          worker ! Requests.Head4s(request)
                      }
                  }
                case Some(deleter) =>
                  // file quarantined, 404
                  sender() ! NotFound()
              }
          }
        }
      }
  }
}
