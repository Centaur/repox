package com.gtan.repox

import java.io.File
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.{ActorRef, Actor, ActorLogging}
import com.ning.http.client.AsyncHandler.STATE
import com.ning.http.client._
import com.typesafe.scalalogging.LazyLogging

/**
 * Created by IntelliJ IDEA.
 * User: xf
 * Date: 14/11/21
 * Time: 下午8:22
 */
object Getter {

  case class UnsuccessResponseStatus(responseStatus: HttpResponseStatus)

  case class BodyPartGot(bodyPart: HttpResponseBodyPart)

  case class HeadersGot(headers: HttpResponseHeaders)

  case class Completed(path: Path)

  case class PeerChosen(who: ActorRef)

  case object Cleanup

}

class Getter(upstream: String, uri: String, requestHeaders: FluentCaseInsensitiveStringsMap) extends Actor with ActorLogging {

  var tempFileChannel: AsynchronousFileChannel = null
  var tempFile: File = null

  import com.gtan.repox.Getter._

  val handler = new AsyncHandler[Unit] with LazyLogging{

    private val canceled = new AtomicBoolean(false)

    def cancel() {
      canceled.set(true)
    }

    override def onThrowable(t: Throwable): Unit = {}

    override def onCompleted(): Unit = {
      if (!canceled.get) {
        self ! Completed(tempFile.toPath)
      }
    }

    override def onBodyPartReceived(bodyPart: HttpResponseBodyPart): STATE = {
      if (!canceled.get()) {
        context.self ! BodyPartGot(bodyPart)
        STATE.CONTINUE
      } else {
        STATE.ABORT
      }
    }

    override def onStatusReceived(responseStatus: HttpResponseStatus): STATE = {
      if (canceled.get()) {
        STATE.ABORT
      } else {
        logger.debug(s"statusCode from $upstreamUrl: ${responseStatus.getStatusCode}")
        if (responseStatus.getStatusCode != 200) {
          UnsuccessResponseStatus(responseStatus)
          STATE.ABORT
        } else
          STATE.CONTINUE
      }
    }

    override def onHeadersReceived(headers: HttpResponseHeaders): STATE = {
      logger.debug(s"Got headers From $upstreamUrl")
      logger.debug(s"headers ================== \n ${headers.getHeaders}")
      if (!
        canceled.get()) {
        self ! HeadersGot(headers)
        STATE.CONTINUE
      } else {
        STATE.ABORT
      }
    }
  }

  def cleanup(): Unit = {
    if (tempFileChannel != null && tempFileChannel.isOpen) {
      log.debug(s"$self cleaning up: closing file channel")
      tempFileChannel.close()
    }
    if (tempFile != null) {
      log.debug(s"$self cleaning up: deleting ${tempFile.toPath.toString}")
      tempFile.delete()
    }

  }

  override def receive() = {
    case Cleanup =>
      cleanup()

    case PeerChosen(who) =>
      log.debug(s"peer $who is chosen. cancel myself $self")
      handler.cancel()
      cleanup()

    case UnsuccessResponseStatus(status) =>

    case HeadersGot(headers) =>
      tempFile = File.createTempFile("repox", ".tmp")
      tempFileChannel = AsynchronousFileChannel.open(tempFile.toPath)
      context.parent ! HeadersGot(headers)

    case BodyPartGot(bodyPart) =>
      val buffer = bodyPart.getBodyByteBuffer
      tempFileChannel.write(buffer, tempFileChannel.size())

    case Completed(path) =>
      if (tempFileChannel.isOpen)
        tempFileChannel.close()
      context.parent ! Completed(path)
  }


  val upstreamUrl = upstream + uri

  Repox.client.prepareGet(upstreamUrl)
    .setHeaders(requestHeaders)
    .execute(handler)
}


