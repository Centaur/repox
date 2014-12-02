package com.gtan.repox

import akka.actor._
import io.undertow.server.HttpServerExchange
import io.undertow.util.StatusCodes

import scala.collection.Set
import scala.util.Random

case class Queue(method: Symbol, uri: String)

object RequestQueueMaster {

  // send by ConfigPersister
  case object ConfigLoaded

  // send by QueueWorker
  case class Dead(queue: Queue)

  // send by FileDeleter
  case class Quarantine(uri: String)

  case class FileDeleted(uri: String)

}

class RequestQueueMaster extends Actor with Stash with ActorLogging {

  import RequestQueueMaster._

  var children = Map.empty[Queue, ActorRef]
  // Quuee -> Get/HeadQueueWorker
  var quarantined = Map.empty[String, ActorRef] // Uri -> FileDeleter

  override def receive = waitingConfigRecover

  def waitingConfigRecover: Receive = {
    case ConfigLoaded =>
      log.debug(s"Config loaded, data: \n ${Config.get}")
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
      log.debug(s"Redownloading $uri(and ${uri+".sha1"})")
      self ! Requests.Download(uri, Config.repos)
    case Dead(queue) =>
      for (worker <- children.get(queue)) {
        log.debug(s"RequestQueueMaster stopping worker ${worker.path.name}")
        worker ! PoisonPill
      }
      children = children - queue

    case req@Requests.Download(uri, from) =>
      val queue = Queue('get, uri)
      if (!Repox.downloaded(uri)) {
        children.get(queue) match {
          case None =>
            val childName = s"DownloadQueueWorker_${Random.nextInt()}_from_${from.map(_.name).mkString("_")}"
            val worker = context.actorOf(Props(classOf[GetQueueWorker], uri), name = childName)
            children = children.updated(queue, worker)
            worker ! req
          case _ => // downloading proceeding, ignore this one
        }
      } else {
        for (worker <- children.get(queue)) {
          worker ! PoisonPill
        }
      }
    case req@Requests.Get(exchange) =>
      val uri = exchange.getRequestURI
      quarantined.get(uri) match {
        case None =>
          val queue = Queue('get, uri)
          if (Repox.downloaded(uri)) {
            Repox.immediateFile(exchange)
            for (worker <- children.get(queue)) {
              worker ! PoisonPill
            }
          } else {
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
      if (Config.immediate404Rules.exists(_.matches(uri))) {
        Repox.immediate404(exchange)
        for (worker <- children.get(queue)) {
          worker ! PoisonPill
        }
      } else {
        quarantined.get(uri) match {
          case None =>
            if (Repox.downloaded(uri)) {
              Repox.immediateHead(exchange)
              for (worker <- children.get(queue)) {
                worker ! PoisonPill
              }
            } else {
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
