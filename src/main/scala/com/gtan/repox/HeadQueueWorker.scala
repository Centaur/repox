package com.gtan.repox

import akka.actor._
import com.gtan.repox.RequestQueueMaster.KillMe
import com.gtan.repox.config.Config
import com.gtan.repox.data.Repo
import io.undertow.server.HttpServerExchange

import scala.concurrent.duration._
import scala.language.postfixOps

object HeadQueueWorker {

  case class NotFound(exchange: HttpServerExchange)

  case class FoundIn(repo: Repo, headers: Repox.ResponseHeaders, exchange: HttpServerExchange)

}

class HeadQueueWorker(val uri: String) extends Actor with Stash with ActorLogging {

  import HeadQueueWorker._

  override def receive = start

  var found = false
  var resultHeaders: Repox.ResponseHeaders = _

  def start: Receive = {
    case Requests.Head(exchange) =>
      log.debug(s"Recevied Head request of ${exchange.getRequestURI} in START state")
      assert(exchange.getRequestURI == uri)
      Repox.downloaded(uri) match {
        case Some(Tuple2(resourceManager, _)) =>
          Repox.immediateHead(resourceManager, exchange)
          suicide()
        case None =>
          for (peers <- Repox.peer(uri)) {
            peers.find(p => Repox.downloaded(p).isDefined) match {
              case Some(peer) =>
                Repox.smart404(exchange)
                suicide()
              case _ =>
                context.actorOf(Props(classOf[HeadMaster], exchange), name = s"HeadMaster_${Repox.nextId}")
                context become working
            }
          }
      }
  }

  def working: Receive = {
    case Requests.Head(exchange) =>
      log.debug(s"Recevied Head request of ${exchange.getRequestURI} in WORKING state")
      assert(exchange.getRequestURI == uri)
      stash()
    case result@FoundIn(repo, headers, exchange) =>
      found = true
      resultHeaders = headers
      Repox.respondHead(exchange, headers)
      log.info(s"Request HEAD for $uri respond 200.")
      unstashAll()
      context.setReceiveTimeout(1 second)
      context become flushWaiting
    case result@NotFound(exchange) =>
      found = false
      Repox.respond404(exchange)
      log.info(s"Tried ${Config.headRetryTimes} times. Give up. Respond with 404. $uri")
      unstashAll()
      context.setReceiveTimeout(1 second)
      context become flushWaiting
  }

  def flushWaiting: Receive = {
    case Requests.Head(exchange) =>
      log.debug(s"Recevied Head request of ${exchange.getRequestURI} in FLUSHWAITING state")
      if (found) {
        log.debug(s"Send found head: $resultHeaders")
        Repox.respondHead(exchange, resultHeaders)
      } else {
        log.debug(s"Send 404")
        Repox.respond404(exchange)
      }
    case ReceiveTimeout =>
      suicide()
  }

  def suicide(): Unit = {
    context.parent ! KillMe(Queue('head, uri))
  }
}
