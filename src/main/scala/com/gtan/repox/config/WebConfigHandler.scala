package com.gtan.repox.config

import io.undertow.Handlers
import io.undertow.server.handlers.resource.ClassPathResourceManager
import io.undertow.server.{HttpHandler, HttpServerExchange}
import io.undertow.util.{Methods, StatusCodes}

import scala.concurrent.duration._

class WebConfigHandler extends HttpHandler {

  override def handleRequest(httpServerExchange: HttpServerExchange) = {
    val (method, uriPrefixed) = (httpServerExchange.getRequestMethod, httpServerExchange.getRequestURI.drop("/config/".length))
    (method, uriPrefixed) match {
      case (Methods.GET, target) if isStaticRequest(target) =>
        Handlers
          .resource(new ClassPathResourceManager(this.getClass.getClassLoader))
          .handleRequest(httpServerExchange)
      case (Methods.GET, "upstreams") =>
//        respondJson(httpServerExchange, Config.repos)
      case (Methods.POST, "upstream") =>
      case (Methods.PUT, "upstream") =>
      case (Methods.GET, "proxies") =>
//        respondJson(httpServerExchange, Config.proxies)
      case (Methods.POST, "proxy") =>
      case (Methods.PUT, "proxy") =>
      case (Methods.GET, "immediate404Rules") =>
//        respondJson(httpServerExchange, Config.immediate404Rules)
      case (Methods.POST, "immediate404Rule") =>
      case (Methods.PUT, "immediate404Rule") =>
      case (Methods.GET, "expireRules") =>
//        respondJson(httpServerExchange, Config.immediate404Rules)
      case (Methods.POST, "expireRule") =>
      case (Methods.PUT, "expireRule") =>
      case (Methods.PUT, "connectionTimeout") =>
        httpServerExchange.getRequestChannel
        Config.set(Config.get.copy(connectionTimeout = 10 seconds))
        respondEmptyOK(httpServerExchange)
      case (Methods.PUT, "connectionIdleTimeout") =>
        respondEmptyOK(httpServerExchange)
    }

  }

  def isStaticRequest(target: String) = Set(".html", ".css", ".js").exists(target.endsWith)
//  def respondJson[T: JsonFormat](exchange: HttpServerExchange, data: T): Unit ={
//    exchange.setResponseCode(StatusCodes.OK)
//    val respondHeaders = exchange.getResponseHeaders
//    respondHeaders.put(Headers.CONTENT_TYPE, "application/json")
//    exchange.getResponseChannel.writeFinal()
//    exchange.endExchange()
//  }

  def respondEmptyOK(exchange: HttpServerExchange): Unit ={
    exchange.setResponseCode(StatusCodes.OK)
    exchange.endExchange()
  }
}
