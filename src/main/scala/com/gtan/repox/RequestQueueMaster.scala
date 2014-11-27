package com.gtan.repox
import akka.actor._
import io.undertow.server.HttpServerExchange
import io.undertow.util.StatusCodes

import scala.util.Random

class RequestQueueMaster extends Actor with ActorLogging {
  var children = Map.empty[Requests.Request, ActorRef] // uri -> RequestQueueWorker

  override def receive = {
    case req@Requests.Get(exchange) =>
      val uri = exchange.getRequestURI
      if (Repox.downloaded(uri)) {
        Repox.immediateFile(exchange)
        for (worker <- children.get(req)) {
          worker ! PoisonPill
        }
      } else {
        children.get(req) match {
          case None =>
            val childName = s"GetQueueWorker_${Random.nextInt()}"
            val worker = context.actorOf(Props(classOf[GetQueueWorker]), name = childName)
            children = children.updated(req, worker)
            worker ! req
          case Some(worker) =>
            worker ! req
        }
      }
    case req@Requests.Head(exchange) =>
      val uri = exchange.getRequestURI
      if (Repox.immediat404Rules.exists(_.matches(uri))) {
        Repox.immediate404(exchange)
        for (worker <- children.get(req)) {
          worker ! PoisonPill
        }
      } else if (Repox.downloaded(uri)) {
        Repox.immediateHead(exchange)
        for (worker <- children.get(req)) {
          worker ! PoisonPill
        }
      } else {
        children.get(req) match {
          case None =>
            val childName = s"HeadQueueWorker_${Random.nextInt()}"
            val worker = context.actorOf(Props(classOf[HeadQueueWorker]), name = childName)
            children = children.updated(req, worker)
            worker ! req
          case Some(worker) =>
            worker ! req
        }
      }
  }
}
