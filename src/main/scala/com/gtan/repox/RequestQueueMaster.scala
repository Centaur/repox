package com.gtan.repox

import akka.actor._
import io.undertow.server.HttpServerExchange
import io.undertow.util.StatusCodes

import scala.util.Random

case class Queue(method: Symbol, uri: String)

class RequestQueueMaster extends Actor with ActorLogging {
  var children = Map.empty[Queue, ActorRef] // Quuee -> Get/HeadQueueWorker

  override def receive = {
    case req@Requests.Download(uri) =>
      val queue = Queue('get, uri)
      if (!Repox.downloaded(uri)) {
        val childName = s"DownloadQueueWorker_${Random.nextInt()}"
        val worker = context.actorOf(Props(classOf[GetQueueWorker], uri), name = childName)
        children = children.updated(queue, worker)
        worker ! req
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
            val worker = context.actorOf(Props(classOf[HeadQueueWorker]), name = childName)
            children = children.updated(queue, worker)
            worker ! req
          case Some(worker) =>
            worker ! req
        }
      }
  }
}
