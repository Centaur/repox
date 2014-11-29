package com.gtan.repox

import akka.actor._
import io.undertow.server.HttpServerExchange
import io.undertow.util.StatusCodes

import scala.util.Random

case class Queue(method: Symbol, uri: String)

object RequestQueueMaster {
  case class Dead(queue: Queue)
}

class RequestQueueMaster extends Actor with ActorLogging {
  import RequestQueueMaster._

  var children = Map.empty[Queue, ActorRef] // Quuee -> Get/HeadQueueWorker

  override def receive = {
    case Dead(queue) =>
      for(worker <- children.get(queue)) {
        log.debug(s"RequestQueueMaster stopping worker ${worker.path.name}")
        worker ! PoisonPill
      }
      children = children - queue

    case req@Requests.Download(uri, from) =>
      val queue = Queue('get, uri)
      if (!Repox.downloaded(uri)) {
        children.get(queue) match {
          case None =>
            val childName = s"DownloadQueueWorker_${Random.nextInt()}_from_${from.name}"
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
    case req@Requests.Head(exchange) =>
      val uri = exchange.getRequestURI
      val queue = Queue('head, uri)
      if (Repox.immediat404Rules.exists(_.matches(uri))) {
        Repox.immediate404(exchange)
        for (worker <- children.get(queue)) {
          worker ! PoisonPill
        }
      } else if (Repox.downloaded(uri)) {
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
  }
}
