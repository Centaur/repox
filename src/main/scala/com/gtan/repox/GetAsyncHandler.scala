package com.gtan.repox

import java.io.{File, FileOutputStream}
import java.nio.channels.FileChannel

import akka.actor.{ActorRef, PoisonPill}
import com.gtan.repox.GetWorker._
import com.gtan.repox.Head404Cache.NotFound
import com.gtan.repox.config.Config
import com.gtan.repox.data.Repo
import com.ning.http.client.AsyncHandler.STATE
import com.ning.http.client.{AsyncHandler, HttpResponseBodyPart, HttpResponseHeaders, HttpResponseStatus}
import com.typesafe.scalalogging.LazyLogging
import io.undertow.util.StatusCodes

class GetAsyncHandler(val uri: String,
                      val repo: Repo,
                      val worker: ActorRef, // associated GetWorker
                      val master: ActorRef, /* associated GetWorker's GetMaster */
                      val tempFilePath: Option[String]) extends AsyncHandler[Unit] with LazyLogging {

  private var tempFileOs: FileOutputStream = null
  private var tempFileChannel: FileChannel = null
  var tempFile: File = null
  private var contentType: String = null

  @volatile private var canceled = false

  def cancel() {
    canceled = true
    cleanup()
  }

  override def onThrowable(t: Throwable): Unit = {
    worker ! AsyncHandlerThrows(t)
  }

  override def onCompleted(): Unit = {
    if (!canceled) {
      if (tempFileOs != null)
        tempFileOs.close()
      if (tempFile != null) {
        // completed before parent notify PeerChosen or self cancel
        master.!(Completed(tempFile.toPath, repo))(worker)
      }
    }
  }

  override def onBodyPartReceived(bodyPart: HttpResponseBodyPart): STATE = {
    if (!canceled) {
      val written = tempFileChannel.write(bodyPart.getBodyByteBuffer)
      worker ! PartialDataReceived(written)
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
    if (canceled) {
      cleanup()
      STATE.ABORT
    } else {
      if (responseStatus.getStatusCode > 200 | responseStatus.getStatusCode >= 300) {
        logger.debug(s"Get ${repo.absolute(uri)} ${responseStatus.getStatusCode}")
        master.!(UnsuccessResponseStatus(responseStatus))(worker)
        cleanup()
        STATE.ABORT
      } else
        STATE.CONTINUE
    }
  }

  override def onHeadersReceived(headers: HttpResponseHeaders): STATE = {
    logger.debug(s"${repo.absolute(uri)} headers ================== \n ${headers.getHeaders}")
    if (!canceled) {
      val newContentType = headers.getHeaders.getFirstValue("Content-type")
      if (contentType == null || contentType == newContentType) {
        if (contentType == null) contentType = newContentType
        if (tempFile != null) {
          logger.debug("Lantern interrupted. Resync data.")
          tempFileOs.close()
          tempFileOs = new FileOutputStream(tempFile)
          tempFileChannel = tempFileOs.getChannel
          tempFileChannel.position(tempFileChannel.size())
          worker ! HeadersGot(headers)
        } else {
          tempFile = newOrReuseTempFile(tempFilePath)
          tempFileOs = new FileOutputStream(tempFile)
          tempFileChannel = tempFileOs.getChannel
          tempFileChannel.position(tempFileChannel.size())
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

  private def newOrReuseTempFile(path: Option[String]): File = {
    path match {
      case None =>
        val parent = Config.storagePath.resolve("temp").toFile
        parent.mkdirs()
        File.createTempFile("repox", ".tmp", parent)
      case Some(file) =>
        new File(file)
    }
  }

}
