package com.gtan.repox

import java.io.{OutputStream, File, FileOutputStream}
import java.nio.file.Path
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPOutputStream

import akka.actor._
import com.gtan.repox.config.Config
import com.gtan.repox.data.Repo
import com.ning.http.client.AsyncHandler.STATE
import com.ning.http.client._
import com.typesafe.scalalogging.LazyLogging

import scala.language.postfixOps

/**
 * Created by IntelliJ IDEA.
 * User: xf
 * Date: 14/11/21
 * Time: 下午8:22
 */
object GetWorker {

  case class UnsuccessResponseStatus(responseStatus: HttpResponseStatus)

  case class Failed(t: Throwable)

  case class BodyPartGot(bodyPart: HttpResponseBodyPart)

  case class HeadersGot(headers: HttpResponseHeaders)

  case class Completed(path: Path, repo: Repo)

  case class PeerChosen(who: ActorRef)

  case object Cleanup

  case object LanternGiveup // same effect as ReceiveTimeout

  case class HeartBeat(length: Int)

  case object WorkerDead

  case class AsyncHandlerThrows(t: Throwable)
}

class GetWorker(upstream: Repo, uri: String, requestHeaders: FluentCaseInsensitiveStringsMap) extends Actor with Stash with ActorLogging {
  import com.gtan.repox.GetWorker._

  val upstreamUrl = upstream.base + uri
  val handler = new GetAsyncHandler(uri, upstream, context.self, context.parent)

  import scala.concurrent.duration._

  var downloaded = 0
  var percentage = 0.0
  var contentLength = -1L

  val client = Config.clientOf(upstream)

  val future = client.prepareGet(upstreamUrl)
    .setHeaders(requestHeaders)
    .execute(handler)

  override def receive = {
    case AsyncHandlerThrows(t) =>
        t.printStackTrace()
        context.parent ! Failed(t)
        self ! PoisonPill

    case Cleanup =>
      handler.cancel()

    case PeerChosen(who) =>
      handler.cancel()

    case ReceiveTimeout | LanternGiveup =>
      context.parent ! Failed(new RuntimeException("Chosen worker timeout or lantern giveup"))
      handler.cancel()
      self ! PoisonPill

    case HeartBeat(length) =>
      downloaded += length
      if(contentLength != -1) {
        val newPercentage = downloaded * 100.0 / contentLength
        if(newPercentage - percentage > 10.0 || downloaded == contentLength) {
          log.debug(f"downloaded $downloaded%s bytes. $newPercentage%.2f %%")
          percentage = newPercentage
        }
      } else {
        log.debug("Heartbeat")
      }

    case HeadersGot(headers) =>
      val contentLengthHeader = headers.getHeaders.getFirstValue("Content-Length")
      if(contentLengthHeader != null) {
        log.debug(s"contentLength=$contentLengthHeader")
        contentLength = contentLengthHeader.toLong
      }
      downloaded = 0
      percentage = 0.0
      context.setReceiveTimeout(Config.getDataTimeout)
  }


}


