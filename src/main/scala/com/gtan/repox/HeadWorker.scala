package com.gtan.repox

import java.net.URL

import akka.actor._
import com.gtan.repox.HeadMaster.{HeadTimeout, NotFound, FoundIn}
import com.ning.http.client.FluentCaseInsensitiveStringsMap
import io.undertow.server.HttpServerExchange
import io.undertow.util.{Headers, StatusCodes}
import concurrent.duration._
import scala.language.postfixOps
import collection.JavaConverters._

object HeadWorker {

  // sent by HeadAsyncHandler
  case class Responded(statusCode: Int, headers: Repox.ResponseHeaders)

  case class Failed(t: Throwable)

}

class HeadWorker(val repo: Repo,
                 val uri: String,
                 val requestHeaders: FluentCaseInsensitiveStringsMap) extends Actor with ActorLogging {

  import HeadWorker._

  val upstreamUrl = repo.base + uri
  val handler = new HeadAsyncHandler(self, uri, repo)

  context.setReceiveTimeout(3 seconds)

  val upstreamHost = new URL(upstreamUrl).getHost
  requestHeaders.put(Headers.HOST_STRING, List(upstreamHost).asJava)

  val client = repo.name match {
    case "typesafe" => Repox.proxyClient
    case _ => Repox.client
  }

  val requestMethod = if (repo.getOnly) client.prepareGet _ else client.prepareHead _
  requestMethod.apply(upstreamUrl)
    .setHeaders(requestHeaders)
    .execute(handler)

  override def receive = {
    case Responded(statusCode, headers) =>
      handler.cancel()
      statusCode match {
        case StatusCodes.OK =>
          log.debug(s"HeadWorker ${repo.name} 200. $uri")
          context.parent ! HeadMaster.FoundIn(repo, headers)
        case StatusCodes.NOT_FOUND =>
          log.debug(s"HeadWorker ${repo.name} got 404.  $uri")
          context.parent ! HeadMaster.NotFound(repo)
        case _ =>
          // server error? further feature may use failed time information
          log.debug(s"HeadWorker ${repo.name} got undetermined result. $uri")
          context.parent ! HeadMaster.HeadTimeout(repo)
      }
      self ! PoisonPill
    case Failed(t) =>
      // further feature may use failed time information
      handler.cancel()
      log.debug(s"HeadWorker ${repo.name} failed. $uri")
      context.parent ! HeadMaster.HeadTimeout(repo)
      self ! PoisonPill
    case ReceiveTimeout =>
      handler.cancel()
      log.debug(s"HeadWorker ${repo.name} timeout. $uri")
      context.parent ! HeadMaster.HeadTimeout(repo)
      self ! PoisonPill
  }
}
