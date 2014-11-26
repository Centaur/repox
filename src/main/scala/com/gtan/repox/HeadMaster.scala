package com.gtan.repox

import akka.actor.Actor.Receive
import akka.actor._
import com.gtan.repox.Repox.ResponseHeaders
import io.undertow.server.HttpServerExchange
import io.undertow.util.StatusCodes

/**
 * Created by xf on 14/11/26.
 */

object HeadMaster {
  trait HeadResult
  case class FoundIn(repo: Repo, headers: ResponseHeaders) extends HeadResult
  case class NotFound(repo: Repo, statusCode: Int) extends HeadResult
  case class HeadTimeout(repo: Repo) extends HeadResult
}

class HeadMaster(exchange: HttpServerExchange) extends Actor with ActorLogging{
  import com.gtan.repox.HeadMaster._

  val uri = exchange.getRequestURI

  private def immediat404(uri: String): Boolean = {
    Repox.immediat404Rules.exists(_.matches(uri))
  }

  if(immediat404(uri)) {
    log.debug(s"$uri immediat 404")
    exchange.setResponseCode(StatusCodes.NOT_FOUND)
    exchange.endExchange()
    self ! PoisonPill
  }

  val children = for(upstream <- Repox.upstreams) yield {
    val childName = upstream.name
    context.actorOf(
      Props(classOf[HeadWorker], upstream, uri),
      name = s"Header_$childName")
  }

  var resultMap = Map.empty[ActorRef, HeadResult]
  var finishedChildren = 0
  var retryTimes = 0

  override def receive: Receive = {
    case msg @ FoundIn(repo, headers) =>
      finishedChildren += 1
      resultMap = resultMap.updated(sender(), msg)
      if(resultMap.isEmpty){ // first 200
        exchange.setResponseCode(StatusCodes.NO_CONTENT) // no content in body
        exchange.getResponseHeaders
        exchange.getResponseChannel // just to avoid mysterious setting Content-length to 0 in endExchange, ugly
        exchange.endExchange()
      }
      if(finishedChildren == children.size) {
        Repox.headerCache ! resultMap
        self ! PoisonPill
      }
    case msg @ NotFound(repo, statusCode) =>
      finishedChildren += 1
      resultMap = resultMap.updated(sender(), msg)
      if(finishedChildren == children.size) {
        if(retryTimes == 3) {
          exchange.setResponseCode(StatusCodes.NOT_FOUND)
          exchange.endExchange()
          Repox.headerCache ! resultMap
          self ! PoisonPill
        } else {
        }
      }
    case msg @ HeadTimeout(repo) =>
      finishedChildren += 1
      resultMap = resultMap.updated(sender(), msg)
      if(finishedChildren == children.size) {
        exchange.setResponseCode(StatusCodes.NOT_FOUND)
        exchange.endExchange()
        Repox.headerCache ! resultMap
        self ! PoisonPill
      }

  }
}
