package com.gtan.repox

import akka.actor.Actor.Receive
import akka.actor._
import com.gtan.repox.Repox.ResponseHeaders
import com.ning.http.client.FluentCaseInsensitiveStringsMap
import io.undertow.server.HttpServerExchange
import io.undertow.util.{Headers, StatusCodes}
import collection.JavaConverters._
import scala.util.Random

/**
 * Created by xf on 14/11/26.
 */

object HeadMaster {
  trait HeadResult
  case class FoundIn(repo: Repo, headers: ResponseHeaders, exchange: HttpServerExchange) extends HeadResult
  case class NotFound(repo: Repo, exchange: HttpServerExchange) extends HeadResult
  case class HeadTimeout(repo: Repo) extends HeadResult
}

class HeadMaster(val exchange: HttpServerExchange) extends Actor with ActorLogging{
  import com.gtan.repox.HeadMaster._

  val uri = exchange.getRequestURI

  val requestHeaders = new FluentCaseInsensitiveStringsMap()
  for (name <- exchange.getRequestHeaders.getHeaderNames.asScala) {
    requestHeaders.add(name.toString, exchange.getRequestHeaders.get(name))
  }
  requestHeaders.put(Headers.ACCEPT_ENCODING_STRING, List("identity").asJava)


  val children = for(upstream <- Repox.upstreams) yield {
    val childName = s"HeaderWorker_${upstream.name}_${Random.nextInt()}"
    context.actorOf(
      Props(classOf[HeadWorker], upstream, uri, requestHeaders),
      name = childName)
  }

  var resultMap = Map.empty[ActorRef, HeadResult]
  var finishedChildren = 0
  var retryTimes = 0

  override def receive: Receive = {
    case msg @ FoundIn(repo, headers) =>
      finishedChildren += 1
      resultMap = resultMap.updated(sender(), msg)
      if(resultMap.isEmpty){ // first 200
        context.parent ! Foundin(repo, headers, exchange)
        self ! PoisonPill
      }
      if(finishedChildren == children.size) {
        self ! PoisonPill
      }
    case msg @ NotFound(repo, statusCode) =>
      finishedChildren += 1
      resultMap = resultMap.updated(sender(), msg)
      if(finishedChildren == children.size) { // all return 404
        if(retryTimes == 3) {
          context.parent ! NotFound(exchange)
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
        Repox.headResultCache ! resultMap
        self ! PoisonPill
      }

  }
}
