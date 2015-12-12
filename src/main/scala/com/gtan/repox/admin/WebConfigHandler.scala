package com.gtan.repox.admin

import java.nio.ByteBuffer

import com.google.common.base.Charsets
import com.gtan.repox.Repox
import com.gtan.repox.config.Config
import io.undertow.Handlers
import io.undertow.server.handlers.resource.ClassPathResourceManager
import io.undertow.server.{HttpHandler, HttpServerExchange}
import io.undertow.util.{Headers, Methods, StatusCodes}
import play.api.libs.json.Format

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}


object WebConfigHandler {
  def handle(httpServerExchange: HttpServerExchange) = {
    implicit val exchange = httpServerExchange
    val (method, uriUnprefixed) = (
      httpServerExchange.getRequestMethod,
      httpServerExchange.getRequestURI.drop("/admin/".length))
    restHandlers.map(_.route).reduce(_ orElse _).apply(method -> uriUnprefixed)
  }

  val restHandlers: Seq[RestHandler] = List(
    StaticAssetHandler,
    AuthHandler,
    UpstreamsHandler,
    ConnectorsHandler,
    ProxiesHandler,
    Immediate404RulesHandler,
    ExpireRulesHandler,
    ParametersHandler,
    ResetHandler
  )

  def setConfigAndRespond(exchange: HttpServerExchange, result: Future[Any]): Unit = {
    result.onComplete {
      case Success(_) =>
        respondEmptyOK(exchange)
      case Failure(t) =>
        respondError(exchange, t)
    }
  }

  def isStaticRequest(target: String) = Set(".html", ".css", ".js", ".ico", ".ttf", ".map", "woff", ".svg", "otf", "png", "jpg", "gif").exists(target.endsWith)

  def respondJson[T: Format](exchange: HttpServerExchange, data: T): Unit = {
    exchange.setStatusCode(StatusCodes.OK)
    val respondHeaders = exchange.getResponseHeaders
    respondHeaders.put(Headers.CONTENT_TYPE, "application/json")
    val json = implicitly[Format[T]].writes(data)
    exchange.getResponseChannel.writeFinal(ByteBuffer.wrap(json.toString().getBytes(Charsets.UTF_8)))
    exchange.endExchange()
  }

  def respondError(exchange: HttpServerExchange, t: Throwable): Unit = {
    exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR)
    exchange.getResponseChannel
    exchange.endExchange()
  }

  def respondEmptyOK(exchange: HttpServerExchange): Unit = {
    exchange.setStatusCode(StatusCodes.OK)
    exchange.getResponseChannel
    exchange.endExchange()
  }

}