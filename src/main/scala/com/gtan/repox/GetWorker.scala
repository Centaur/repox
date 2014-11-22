package com.gtan.repox

import java.io.{File, FileOutputStream}
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.{Actor, ActorLogging, ActorRef}
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

}

class GetWorker(upstream: String, uri: String, requestHeaders: FluentCaseInsensitiveStringsMap) extends Actor with ActorLogging {


  import com.gtan.repox.GetWorker._

  val handler = new AsyncHandler[Unit] with LazyLogging{
    var tempFileOs: FileOutputStream = null
    var tempFile: File = null

    private val canceled = new AtomicBoolean(false)

    def cancel() {
      canceled.set(true)
      cleanup()
    }

    override def onThrowable(t: Throwable): Unit = {}

    override def onCompleted(): Unit = {
      logger.debug(s"$self completed, canceled: ${canceled.get}")
      if (!canceled.get) {
        if (tempFileOs != null)
          tempFileOs.close()
        context.parent ! Completed(tempFile.toPath)
      }
    }

    override def onBodyPartReceived(bodyPart: HttpResponseBodyPart): STATE = {
      logger.debug(s"$self got bodypart, canceled: ${canceled.get}")
      if (!canceled.get()) {
        bodyPart.writeTo(tempFileOs)
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
        logger.debug(s"statusCode from $upstreamUrl: ${responseStatus.getStatusCode}")
        if (responseStatus.getStatusCode != 200) {
          UnsuccessResponseStatus(responseStatus)
          cleanup()
          STATE.ABORT
        } else
          STATE.CONTINUE
      }
    }

    override def onHeadersReceived(headers: HttpResponseHeaders): STATE = {
      logger.debug(s"Got headers From $upstreamUrl")
      logger.debug(s"headers ================== \n ${headers.getHeaders}")
      if (!canceled.get()) {
        tempFile = File.createTempFile("repox", ".tmp")
        tempFileOs = new FileOutputStream(tempFile)
        context.parent ! HeadersGot(headers)
        STATE.CONTINUE
      } else {
        cleanup()
        STATE.ABORT
      }
    }

    def cleanup(): Unit = {
      if (tempFileOs != null) {
        log.debug(s"$self cleaning up: closing file channel")
        tempFileOs.close()
      }
      if (tempFile != null) {
        log.debug(s"$self cleaning up: deleting ${tempFile.toPath.toString}")
        tempFile.delete()
      }
      log.debug(s"$self stopping myself")
      context.stop(self)
    }
  }


  override def receive() = {
    case Cleanup =>
      log.debug(s"$self cleanedup.")
      handler.cleanup()

    case PeerChosen(who) =>
      log.debug(s"peer $who is chosen. cancel myself $self")
      handler.cancel()

    case UnsuccessResponseStatus(status) =>

    case HeadersGot(headers) =>
      context.parent ! HeadersGot(headers)

  }


  val upstreamUrl = upstream + uri

  Repox.client.prepareGet(upstreamUrl)
    .setHeaders(requestHeaders)
    .execute(handler)
}


