package com.gtan.repox

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.ActorRef
import com.ning.http.client.AsyncHandler.STATE
import com.ning.http.client.{AsyncHandler, HttpResponseBodyPart, HttpResponseHeaders, HttpResponseStatus}
import com.typesafe.scalalogging.LazyLogging
import io.undertow.util.StatusCodes

class HeadAsyncHandler(val worker: ActorRef,val uri: String, val repo: Repo) extends AsyncHandler[Unit] with LazyLogging {
  var statusCode = 200

  private val canceled = new AtomicBoolean(false)

  def cancel(): Unit = {
    canceled.set(true)
  }

  override def onThrowable(t: Throwable): Unit = {
    if(!canceled.get()) {
      t.printStackTrace()
      worker ! HeadWorker.Failed(t)
    }
  }

  override def onCompleted(): Unit = {
    /* we don't get here actually */
  }

  override def onBodyPartReceived(bodyPart: HttpResponseBodyPart): STATE = {
    // we don't get here actually
    STATE.ABORT
  }

  override def onStatusReceived(responseStatus: HttpResponseStatus): STATE = {
    if (!canceled.get) {
      statusCode = responseStatus.getStatusCode
      statusCode match {
        case StatusCodes.NOT_FOUND =>
          Repox.head404Cache ! Head404Cache.NotFound(uri, repo)
        case _ =>
      }
      STATE.CONTINUE
    } else STATE.ABORT
  }

  override def onHeadersReceived(headers: HttpResponseHeaders): STATE = {
    logger.debug(s"HeadAsyncHandler of ${worker.path.name} statusCode = $statusCode canceled = ${canceled.get} $uri")
    if (!canceled.get) {
      import scala.collection.JavaConverters._
      worker ! HeadWorker.Responded(statusCode, mapAsScalaMapConverter(headers.getHeaders).asScala.toMap)
    }
    STATE.ABORT
  }

}
