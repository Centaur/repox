package com.gtan.repox
import akka.actor._
import io.undertow.server.HttpServerExchange

import scala.concurrent.duration._
import scala.language.postfixOps

class GetQueueWorker extends Actor with Stash with ActorLogging {
  override def receive = idle

  def idle: Receive = {
    case Requests.Get(exchange) =>
      val uri = exchange.getRequestURI
      if (Repox.downloaded(uri)) {
        Repox.immediateFile(exchange)
        self ! PoisonPill
      } else {
        val resolvedPath = Repox.storage.resolve(uri.tail)
        GetMaster.run(exchange, resolvedPath, Repox.candidates)
        context become working
      }
  }

  def working: Receive = {
    case Requests.Get(_) =>
      stash()
    case result @ GetWorker.Completed(path, repo) =>
      unstashAll()
      context.setReceiveTimeout(1 second)
      context become flushWaiting
  }

  def flushWaiting: Receive = {
    case Requests.Get(exchange) =>
      exchange.endExchange
    case ReceiveTimeout =>
      self ! PoisonPill
  }
}
