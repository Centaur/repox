package com.gtan.repox

import java.io.{FileOutputStream, File, OutputStream}
import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.{PoisonPill, ActorRef}
import com.gtan.repox.GetWorker._
import com.gtan.repox.HeaderCache.Found
import com.ning.http.client.AsyncHandler.STATE
import com.ning.http.client.{HttpResponseHeaders, HttpResponseStatus, HttpResponseBodyPart, AsyncHandler}
import com.typesafe.scalalogging.LazyLogging

class GetAsyncHandler(val upstreamUrl: String, val worker: ActorRef, val master: ActorRef) extends AsyncHandler[Unit] with LazyLogging {
  var tempFileOs: OutputStream = null
  var tempFile: File = null

  private val canceled = new AtomicBoolean(false)

  def cancel() {
    canceled.set(true)
    cleanup()
  }

  override def onThrowable(t: Throwable): Unit = {
    worker ! AsyncHandlerThrows(t)
  }

  override def onCompleted(): Unit = {
    if (!canceled.get()) {
      logger.debug(s"asynchandler of $worker completed")
      if (tempFileOs != null)
        tempFileOs.close()
      if (tempFile != null) {
        // completed before parent notify PeerChosen or self cancel
        master.!(Completed(tempFile.toPath))(worker)
      }
    }
  }

  override def onBodyPartReceived(bodyPart: HttpResponseBodyPart): STATE = {
    if (!canceled.get()) {
      bodyPart.writeTo(tempFileOs)
      worker ! HeartBeat(bodyPart.length())
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
        master.!(UnsuccessResponseStatus(responseStatus))(worker)
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
      worker ! HeadersGot(headers)
      master.!(HeadersGot(headers))(worker)
      STATE.CONTINUE
    } else {
      cleanup()
      STATE.ABORT
    }
  }

  def cleanup(): Unit = {
    if (tempFileOs != null) {
      logger.debug(s"$worker closing file channel")
      tempFileOs.close()
    }
    if (tempFile != null) {
      logger.debug(s"$worker deleting ${tempFile.toPath.toString}")
      tempFile.delete()
    }
    worker ! PoisonPill
  }

}
