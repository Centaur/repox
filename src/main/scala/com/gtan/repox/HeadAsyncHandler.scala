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
    worker ! HeadWorker.Failed(t)
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
          Repox.headResultCache ! HeadResultCache.NotFound(uri, repo)
      }
      STATE.CONTINUE
    } else STATE.ABORT
  }

  override def onHeadersReceived(headers: HttpResponseHeaders): STATE = {
    if (!canceled.get) {
      import scala.collection.JavaConverters._
      worker ! HeadWorker.Responded(statusCode, mapAsScalaMapConverter(headers.getHeaders).asScala.toMap)
    }
    STATE.ABORT
  }

}
