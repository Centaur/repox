package com.gtan.repox

import java.net.URL
import java.util

import com.ning.http.client.AsyncHandler.STATE
import com.ning.http.client._
import io.undertow.server.HttpServerExchange
import io.undertow.util.HttpString

import scala.concurrent.{Promise, Future}
import scala.util.{Failure, Success}
import collection.JavaConverters._

/**
 * Created by xf on 14/11/20.
 */
object Repox {
  import scala.concurrent.ExecutionContext.Implicits.global

  val upstreams = List(
    "http://uk.maven.org/maven2",
    "http://maven.oschina.net/content/groups/public",
    "http://repo1.maven.org/maven2"
  )
  val client = new AsyncHttpClient()

  type StatusCode = Int
  type ResponseHeaders = Map[String, java.util.List[String]]

  type HeaderResponse = (StatusCode, ResponseHeaders)

  def tryHead(exchange: HttpServerExchange, upstreamUrl: String): Future[HeaderResponse] = {
    val promise = Promise[HeaderResponse]()
    var statusCode = 200

    val requestHeaders = new FluentCaseInsensitiveStringsMap()
    for (name <- exchange.getRequestHeaders.getHeaderNames.asScala) {
      requestHeaders.add(name.toString, exchange.getRequestHeaders.get(name))
    }
    val upstreamHost = new URL(upstreamUrl).getHost
    requestHeaders.put("Host", List(upstreamHost).asJava)

    client.prepareHead(upstreamUrl)
      .setHeaders(requestHeaders)
      .execute(new AsyncHandler[Unit] {
      override def onThrowable(t: Throwable): Unit = promise.failure(t)

      override def onCompleted(): Unit = {
        /* we don't get here actually */
      }

      override def onBodyPartReceived(bodyPart: HttpResponseBodyPart): STATE = {
        // we don't get here actually
        STATE.CONTINUE
      }

      override def onStatusReceived(responseStatus: HttpResponseStatus): STATE = {
        if(!promise.isCompleted) {
          statusCode = responseStatus.getStatusCode
          println(s"statusCode from $upstreamUrl: $statusCode")
          STATE.CONTINUE
        } else STATE.ABORT
      }

      override def onHeadersReceived(headers: HttpResponseHeaders): STATE = {
        if(!promise.isCompleted) {
          import collection.JavaConverters._
          println(s"Got headers From $upstreamUrl")
          promise.success(statusCode -> mapAsScalaMapConverter(headers.getHeaders).asScala.toMap)
        }
        STATE.ABORT
      }
    })

    import scala.concurrent.duration._
    TimeoutableFuture(promise, after = 5 seconds, name = upstreamHost)
  }

  def handle(exchange: HttpServerExchange): Unit = {

    exchange.getRequestMethod.toString.toUpperCase match {
      case "HEAD" =>
        val firstSuccess = Future.find(upstreams.map(upstream => tryHead(exchange, upstream + exchange.getRequestURI))) {
          case (200, _) => true
          case _ => false
        }
        firstSuccess.onComplete {
          case Success(Some(Pair(statusCode, headers))) =>
            println(s"statusCode=$statusCode, headers=$headers")
            exchange.setResponseCode(statusCode)
            for ((k, v) <- headers) {
              exchange.getResponseHeaders.addAll(new HttpString(k), v)
            }
            val contentLength = exchange.getResponseHeaders.getFirst(new HttpString("Content-Length")).toLong
            println(contentLength)
            exchange.setResponseContentLength(contentLength)
            exchange.endExchange()
          case Success(None) | Failure(_) =>
            exchange.setResponseCode(404)
            exchange.endExchange()
        }
    }
  }
}
