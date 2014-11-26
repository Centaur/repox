package com.gtan.repox

import com.gtan.repox.HeaderCache.NotFound
import com.gtan.repox.Repox.HeaderResponse
import com.ning.http.client.AsyncHandler.STATE
import com.ning.http.client.{HttpResponseHeaders, HttpResponseStatus, HttpResponseBodyPart, AsyncHandler}
import com.typesafe.scalalogging.LazyLogging
import io.undertow.util.StatusCodes

import scala.concurrent.Promise

class HeadAsyncHandler(val upstream: Repo, val promise: Promise[HeaderResponse]) extends AsyncHandler[Unit] with LazyLogging{
  var statusCode = 200

  override def onThrowable(t: Throwable): Unit = promise.failure(t)

  override def onCompleted(): Unit = {
    /* we don't get here actually */
  }

  override def onBodyPartReceived(bodyPart: HttpResponseBodyPart): STATE = {
    // we don't get here actually
    STATE.ABORT
  }

  override def onStatusReceived(responseStatus: HttpResponseStatus): STATE = {
    if (!promise.isCompleted) {
      statusCode = responseStatus.getStatusCode
      STATE.CONTINUE
    } else STATE.ABORT
  }

  override def onHeadersReceived(headers: HttpResponseHeaders): STATE = {
    if (!promise.isCompleted) {
      import scala.collection.JavaConverters._
      promise.success(upstream, statusCode, mapAsScalaMapConverter(headers.getHeaders).asScala.toMap)
    }
    STATE.ABORT
  }

}
