package com.gtan.repox

import java.nio.file.Path

import akka.actor._
import io.undertow.server.HttpServerExchange

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random
import scala.util.Random

object GetQueueWorker {
  case class Get404(uri: String)
  case class Completed(path: Path, repo: Repo, exchange: HttpServerExchange)
}
class GetQueueWorker extends Actor with Stash with ActorLogging {
  import GetQueueWorker._

  override def receive = idle

  def idle: Receive = {
    case Requests.Get(exchange) =>
      val uri = exchange.getRequestURI
      if (Repox.downloaded(uri)) {
        log.info(s"$uri downloaded. Serve immediately.")
        Repox.immediateFile(exchange)
        self ! PoisonPill
      } else {
        log.info(s"$uri not downloaded. Downloading.")
        val resolvedPath = Repox.storage.resolve(uri.tail)
        context.actorOf(Props(classOf[GetMaster], exchange, resolvedPath), s"GetMaster_${Random.nextInt}")
        context become working
      }
  }

  def working: Receive = {
    case Requests.Get(_) =>
      stash()
    case result @ Completed(path, repo, exchange) =>
      log.debug(s"GetQueue completed $path")
      Repox.immediateFile(exchange)
      unstashAll()
      context.setReceiveTimeout(1 second)
      context become flushWaiting
    case Get404(uri) =>
      log.debug(s"GetQueueWorker 404 $uri")
      unstashAll()
      context.setReceiveTimeout(1 second)
      context become flushWaiting
  }

  def flushWaiting: Receive = {
    case Requests.Get(exchange) =>
      Repox.immediateFile(exchange)
      exchange.endExchange
    case ReceiveTimeout =>
      self ! PoisonPill
  }
}
