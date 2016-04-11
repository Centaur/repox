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
  var tempFile: File = null
  private var contentType: String = null

  @volatile private var canceled = false

  def cancel(deleteTempFile: Boolean = true) {
    canceled = true
    cleanup(deleteTempFile)
  }

  override def onThrowable(t: Throwable): Unit = {
    if (!canceled)
      worker ! AsyncHandlerThrows(t)
  }

  override def onCompleted(): Unit = {
    if (!canceled) {
      if (tempFileOs != null) {
        logger.debug(s"onCompleted: tempFile.length = ${tempFile.length()}")
        tempFileOs.close()
      }
      if (tempFile != null) {
        // completed before parent notify PeerChosen or self cancel
        master.!(Completed(tempFile.toPath, repo))(worker)
      }
    }
  }

  override def onBodyPartReceived(bodyPart: HttpResponseBodyPart): STATE = {
    if (!canceled) {
      bodyPart.writeTo(tempFileOs)
      worker ! PartialDataReceived(bodyPart.length())
      STATE.CONTINUE
    } else {
      cleanup()
      STATE.ABORT
    }
  }

  override def onStatusReceived(responseStatus: HttpResponseStatus): STATE = {
    val statusCode: Int = responseStatus.getStatusCode
    if (statusCode == StatusCodes.NOT_FOUND) {
      Repox.head404Cache ! NotFound(uri, repo)
    }
    if (canceled) {
      cleanup()
      STATE.ABORT
    } else {
      if (statusCode < 200 || statusCode >= 400) {
        logger.debug(s"Get ${repo.absolute(uri)} $statusCode")
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
          worker ! HeadersGot(headers)
        } else {
          tempFile = newOrReuseTempFile()
          tempFileOs = new FileOutputStream(tempFile, true)
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

  def cleanup(deleteTempFile: Boolean = true): Unit = {
    logger.debug(s"Cleaning up ${tempFile.toPath.toString}")
    if (tempFileOs != null) {
      tempFileOs.close()
    }
    if (tempFile != null && deleteTempFile) {
      tempFile.delete()
    }
  }

  private def newOrReuseTempFile(): File = tempFilePath match {
    case None =>
      val parent = Config.storagePath.resolve("temp").toFile
      parent.mkdirs()
      File.createTempFile("repox", ".tmp", parent)
    case Some(file) =>
      new File(file)
  }

}
