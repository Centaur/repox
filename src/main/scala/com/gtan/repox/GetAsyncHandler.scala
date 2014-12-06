package com.gtan.repox

import java.io.{FileOutputStream, File, OutputStream}
import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.{ReceiveTimeout, PoisonPill, ActorRef}
import com.gtan.repox.GetWorker._
import com.gtan.repox.Head404Cache.NotFound
import com.gtan.repox.data.Repo
import com.ning.http.client.AsyncHandler.STATE
import com.ning.http.client.{HttpResponseHeaders, HttpResponseStatus, HttpResponseBodyPart, AsyncHandler}
import com.typesafe.scalalogging.LazyLogging
import io.undertow.util.StatusCodes

class GetAsyncHandler(val uri: String, val repo: Repo, val worker: ActorRef, val master: ActorRef) extends AsyncHandler[Unit] with LazyLogging {
  val upstreamUrl: String = repo.base + uri

  var tempFileOs: OutputStream = null
  var tempFile: File = null
  var contentType: String = null

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
      if (tempFileOs != null)
        tempFileOs.close()
      if (tempFile != null) {
        // completed before parent notify PeerChosen or self cancel
        master.!(Completed(tempFile.toPath, repo))(worker)
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
    if (responseStatus.getStatusCode == StatusCodes.NOT_FOUND) {
      Repox.head404Cache ! NotFound(uri, repo)
    }
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
      val newContentType = headers.getHeaders.getFirstValue("Content-type")
      if (contentType == null || contentType == newContentType) {
        if (contentType == null) contentType = newContentType
        if (tempFile != null) {
          logger.debug("Lantern interrupted. Resync data.")
          tempFileOs.close()
          tempFileOs = new FileOutputStream(tempFile)
          worker ! HeadersGot(headers)
        } else {
          tempFile = File.createTempFile("repox", ".tmp")
          tempFileOs = new FileOutputStream(tempFile)
          worker ! HeadersGot(headers)
          master.!(HeadersGot(headers))(worker)
        }
        STATE.CONTINUE
      } else {
        worker ! LanternGiveup
        cleanup()
        STATE.ABORT
      }
    } else {
      cleanup()
      STATE.ABORT
    }
  }

  def cleanup(): Unit = {
    if (tempFileOs != null) {
      tempFileOs.close()
    }
    if (tempFile != null) {
      tempFile.delete()
    }
    worker ! PoisonPill
  }

}
