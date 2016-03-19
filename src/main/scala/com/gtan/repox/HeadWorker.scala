package com.gtan.repox

import akka.actor._
import com.gtan.repox.config.Config
import com.gtan.repox.data.Repo
import com.ning.http.client.FluentCaseInsensitiveStringsMap
import io.undertow.util.{Headers, StatusCodes}

import scala.collection.JavaConverters._
import scala.language.postfixOps

object HeadWorker {

  // sent by HeadAsyncHandler
  case class Responded(statusCode: Int, headers: Repox.ResponseHeaders)

  case class Failed(t: Throwable)

}

class HeadWorker(val repo: Repo,
                 val uri: String,
                 val requestHeaders: FluentCaseInsensitiveStringsMap) extends Actor with ActorLogging {

  import HeadWorker._

  val handler = new HeadAsyncHandler(self, uri, repo)

  context.setReceiveTimeout(Config.headTimeout)
  requestHeaders.put(Headers.HOST_STRING, List(repo.host).asJava)

  val (_, client) = Repox.clientOf(repo)

  val requestMethod = if (repo.getOnly) client.prepareGet _ else client.prepareHead _
  requestMethod.apply(repo.absolute(uri))
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
          log.debug(s"HeadWorker ${repo.name} got undetermined result $statusCode for $uri.")
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
