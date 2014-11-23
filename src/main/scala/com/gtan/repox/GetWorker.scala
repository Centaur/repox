package com.gtan.repox

import java.io.{OutputStream, File, FileOutputStream}
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPOutputStream

import akka.actor._
import com.ning.http.client.AsyncHandler.STATE
import com.ning.http.client._
import com.typesafe.scalalogging.LazyLogging

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

  val handler = new AsyncHandler[Unit] with LazyLogging {
    var tempFileOs: OutputStream = null
    var tempFile: File = null

    private val canceled = new AtomicBoolean(false)

    def cancel() {
      canceled.set(true)
      cleanup()
    }

    override def onThrowable(t: Throwable): Unit = {
      self ! AsyncHandlerThrows(t)
    }

    override def onCompleted(): Unit = {
      if (!canceled.get()) {
        logger.debug(s"asynchandler of $self completed")
        if (tempFileOs != null)
          tempFileOs.close()
        if(tempFile != null) { // completed before parent notify PeerChosen or self cancel
          context.parent ! Completed(tempFile.toPath)
        }
      }
    }

    override def onBodyPartReceived(bodyPart: HttpResponseBodyPart): STATE = {
      if (!canceled.get()) {
        bodyPart.writeTo(tempFileOs)
        self ! HeartBeat(bodyPart.length())
        STATE.CONTINUE
      } else {
        cleanup()
        STATE.ABORT
      }
    }

    override def onStatusReceived(responseStatus: HttpResponseStatus): STATE = {
      if (canceled.get()) {
        cleanup()
        STATE.ABORT
      } else {
        if (responseStatus.getStatusCode != 200) {
          logger.debug(s"Get $upstreamUrl ${responseStatus.getStatusCode}")
          context.parent ! UnsuccessResponseStatus(responseStatus)
          cleanup()
          STATE.ABORT
        } else
          STATE.CONTINUE
      }
    }

    override def onHeadersReceived(headers: HttpResponseHeaders): STATE = {
      logger.debug(s"$upstreamUrl 200 headers ================== \n ${headers.getHeaders}")
      if (!canceled.get()) {
        tempFile = File.createTempFile("repox", ".tmp")
        tempFileOs = new FileOutputStream(tempFile)
        self ! HeadersGot(headers)
        context.parent ! HeadersGot(headers)
        STATE.CONTINUE
      } else {
        cleanup()
        STATE.ABORT
      }
    }

    def cleanup(): Unit = {
      if (tempFileOs != null) {
        log.debug(s"$self closing file channel")
        tempFileOs.close()
      }
      if (tempFile != null) {
        log.debug(s"$self deleting ${tempFile.toPath.toString}")
        tempFile.delete()
      }
      self ! PoisonPill
    }
  }

  import scala.concurrent.duration._

  var downloaded = 0
  var percentage = 0.0
  var contentLength = -1L
  var headersGot = false

  override def receive = {
    case AsyncHandlerThrows(t) =>
      throw t

    case Cleanup =>
      log.debug(s"Parent asking cleanup. cancel myself $self")
      handler.cancel()

    case PeerChosen(who) =>
      log.debug(s"peer $who is chosen. cancel myself $self")
      handler.cancel()

    case UnsuccessResponseStatus(status) =>

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
      context.setReceiveTimeout(10 seconds)
      headersGot = true
  }


  val upstreamUrl = upstream.base + uri

  Repox.client.prepareGet(upstreamUrl)
    .setHeaders(requestHeaders)
    .execute(handler)
}


