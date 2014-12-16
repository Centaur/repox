package com.gtan.repox

import akka.actor._
import com.gtan.repox.config.Config
import com.gtan.repox.data.Repo
import io.undertow.server.HttpServerExchange
import org.w3c.dom.html.HTMLScriptElement

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

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
      assert(exchange.getRequestURI == uri)
      if (Repox.downloaded(uri)) {
        Repox.immediateHead(exchange)
        suicide()
      } else {
        Repox.peer(uri) match {
          case Some(peer) if Repox.downloaded(peer) =>
            Repox.smart404(exchange)
            suicide()
          case _ =>
            context.actorOf(Props(classOf[HeadMaster], exchange), name = s"HeadMaster_${Random.nextInt()}")
            context become working
        }
      }
  }

  def working: Receive = {
    case Requests.Head(exchange) =>
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
      if (found)
        Repox.respondHead(exchange, resultHeaders)
      else
        Repox.respond404(exchange)
    case ReceiveTimeout =>
      suicide()
  }

  def suicide(): Unit = {
    val queue = Queue('head, uri)
    log.debug(s"$queue suicide.")
    context.parent ! RequestQueueMaster.Dead(queue)
  }
}
