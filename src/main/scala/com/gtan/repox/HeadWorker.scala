package com.gtan.repox
import java.net.URL

import akka.actor.{PoisonPill, ReceiveTimeout, Actor, ActorLogging}
import com.gtan.repox.HeadMaster.{NotFound, FoundIn}
import com.gtan.repox.Repox._
import com.ning.http.client.FluentCaseInsensitiveStringsMap
import io.undertow.server.HttpServerExchange
import io.undertow.util.{Headers, StatusCodes}
import concurrent.duration._
import scala.language.postfixOps
import collection.JavaConverters._

object HeadWorker {
  // sent by HeadAsyncHandler
  case class Returned(statusCode: Int, headers: Repox.ResponseHeaders)
  case class Failed(t: Throwable)
  // send by HeaderMaster
  case class PeerChosen(peer: Repo)

}

class HeadWorker(val repo: Repo, val uri: String, val requestHeaders: FluentCaseInsensitiveStringsMap) extends Actor with ActorLogging{
  import HeadWorker._

  val upstreamUrl = repo.base + uri
  val handler = new HeadAsyncHandler(self)

  context.setReceiveTimeout(3 seconds)

  val upstreamHost = new URL(upstreamUrl).getHost
  requestHeaders.put(Headers.HOST_STRING, List(upstreamHost).asJava)

  val requestMethod = if(repo.getOnly) client.prepareGet _ else client.prepareHead _
  requestMethod.apply(upstreamUrl)
  .setHeaders(requestHeaders)
  .execute(handler)

  override def receive = {
    case Returned(statusCode, headers) =>
      statusCode match {
        case StatusCodes.OK =>
          context.parent ! FoundIn(repo, headers)
        case StatusCodes.NOT_FOUND =>
          context.parent ! NotFound(repo, statusCode)
        case _ =>
          context.parent ! Undetermined(repo)
      }
    case Failed(t) =>
      context.parent ! Undetermined(repo)
    case ReceiveTimeout =>
      context.parent ! Timeout(repo)
    case PeerChosen(peer) =>
      log.debug(s"HeadMaster chose ${repo.name}, cancel myself ${sender.path.name}."
      handler.cancel()
      self ! PoisonPill

  }
}
