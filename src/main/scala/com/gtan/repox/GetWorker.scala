package com.gtan.repox

import java.io.{OutputStream, File, FileOutputStream}
import java.nio.file.Path
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPOutputStream

import akka.actor._
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

  case class BodyPartGot(bodyPart: HttpResponseBodyPart)

  case class HeadersGot(headers: HttpResponseHeaders)

  case class Completed(path: Path)

  case class PeerChosen(who: ActorRef)

  case object Cleanup

  case class HeartBeat(length: Int)

  case object WorkerDead

  case class AsyncHandlerThrows(t: Throwable)
}

class GetWorker(upstream: Repo, uri: String, requestHeaders: FluentCaseInsensitiveStringsMap) extends Actor with Stash with ActorLogging {
  import com.gtan.repox.GetWorker._

  val upstreamUrl = upstream.base + uri
  val handler = new GetAsyncHandler(upstreamUrl, context.self, context.parent)

  import scala.concurrent.duration._

  var downloaded = 0
  var percentage = 0.0
  var contentLength = -1L
  var headersGot = false

  override def receive = {
    case AsyncHandlerThrows(t) =>
        throw t // retry myself

    case Cleanup =>
      log.debug(s"Parent asking cleanup. cancel myself $self")
      handler.cancel()

    case PeerChosen(who) =>
      log.debug(s"peer $who is chosen. cancel myself $self")
      handler.cancel()

    case ReceiveTimeout =>
      context.parent ! WorkerDead

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
      context.setReceiveTimeout(20 seconds)
      headersGot = true
  }

  val future = Repox.client.prepareGet(upstreamUrl)
    .setHeaders(requestHeaders)
    .execute(handler)
}


