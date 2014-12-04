package com.gtan.repox.admin

import com.gtan.repox.config.Config
import io.undertow.server.HttpServerExchange
import io.undertow.util.Methods

import scala.concurrent.duration._

object ParametersHandler extends RestHandler {

  import WebConfigHandler._

  override def route(implicit exchange: HttpServerExchange) = {
    case (Methods.PUT, "connectionTimeout") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
      setConfigAndRespond(exchange,
        Config.get.copy(connectionTimeout = Duration.apply(newV.toLong, MILLISECONDS)))
    case (Methods.PUT, "connectionIdleTimeout") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
      setConfigAndRespond(exchange,
        Config.get.copy(connectionIdleTimeout = Duration.apply(newV.toLong, MILLISECONDS)))
    case (Methods.PUT, "mainClientMaxConnections") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
      setConfigAndRespond(exchange,
        Config.get.copy(mainClientMaxConnections = newV.toInt))
    case (Methods.PUT, "mainClientMaxConnectionsPerHost") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
      setConfigAndRespond(exchange,
        Config.get.copy(mainClientMaxConnectionsPerHost = newV.toInt))
    case (Methods.PUT, "proxyClientMaxConnections") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
      setConfigAndRespond(exchange,
        Config.get.copy(proxyClientMaxConnections = newV.toInt))
    case (Methods.PUT, "proxyClientMaxConnectionsPerHost") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
      setConfigAndRespond(exchange,
        Config.get.copy(proxyClientMaxConnectionsPerHost = newV.toInt))
  }
}
