package com.gtan.repox

import java.net.URL

import com.gtan.repox.Repox._
import com.ning.http.client.FluentCaseInsensitiveStringsMap
import com.typesafe.scalalogging.LazyLogging
import io.undertow.server.HttpServerExchange
import io.undertow.util.{Headers, StatusCodes, HttpString}

import scala.concurrent.{Promise, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}
import scala.collection.JavaConverters._

class HeadWorker(val exchange: HttpServerExchange) extends LazyLogging{
  import scala.concurrent.ExecutionContext.Implicits.global

  val uri = exchange.getRequestURI

  private def immediat404(uri: String): Boolean = {
    Repox.immediat404Rules.exists(_.matches(uri))
  }

  def `try`(times: Int): Unit ={
    if (immediat404(uri)) {
      logger.debug(s"$uri immediat 404")
      exchange.setResponseCode(StatusCodes.NOT_FOUND)
      exchange.endExchange()
    } else if (times == 0) {
      logger.debug(s"Tried 3 times. Give up.")
      exchange.setResponseCode(StatusCodes.NOT_FOUND)
      exchange.endExchange()
    } else {
      val firstSuccess = Future.find(upstreams.map(run))(_._2 == StatusCodes.OK)

      firstSuccess.onComplete {
        case Success(Some(Tuple3(upstream, statusCode, headers))) =>
          logger.info(s"200 HEAD in ${upstream.name} for $uri")
          exchange.setResponseCode(StatusCodes.NO_CONTENT) // no content in body
          for ((k, v) <- headers) {
            exchange.getResponseHeaders.addAll(new HttpString(k), v.asScala.distinct.asJava)
          }
          exchange.getResponseHeaders.add(new HttpString("Data-Source"), upstream.name)
          exchange.getResponseChannel // just to avoid mysterious setting Content-length to 0 in endExchange, ugly
          exchange.endExchange()
        case Success(None) | Failure(_) =>
          logger.info(s"! No headers found for ${exchange.getRequestURI} ")
          `try`(times - 1)
      }
    }
  }

  val requestHeaders = new FluentCaseInsensitiveStringsMap()
  for (name <- exchange.getRequestHeaders.getHeaderNames.asScala) {
    requestHeaders.add(name.toString, exchange.getRequestHeaders.get(name))
  }
  requestHeaders.put(Headers.ACCEPT_ENCODING_STRING, List("identity").asJava)

  private def run(upstream: Repo): Future[HeaderResponse] = {
    val upstreamUrl = upstream.base + exchange.getRequestURI

    val upstreamHost = new URL(upstreamUrl).getHost
    requestHeaders.put(Headers.HOST_STRING, List(upstreamHost).asJava)

    val promise = Promise[HeaderResponse]()
    client.prepareHead(upstreamUrl)
      .setHeaders(requestHeaders)
      .execute(new HeadAsyncHandler(upstream, promise))

    import scala.concurrent.duration._
    TimeoutableFuture(promise.future, after = 3 seconds, name = upstreamHost)
  }

}
