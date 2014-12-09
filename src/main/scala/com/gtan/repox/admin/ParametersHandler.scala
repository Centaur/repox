package com.gtan.repox.admin

import com.gtan.repox.Repox
import com.gtan.repox.config.Config
import com.gtan.repox.config.ConfigPersister._
import io.undertow.server.HttpServerExchange
import io.undertow.util.Methods
import play.api.libs.json.{JsNumber, JsString, JsObject}
import collection.JavaConverters._
import scala.concurrent.duration._
import akka.pattern.ask

import scala.language.postfixOps

object ParametersHandler extends RestHandler {

  import WebConfigHandler._

  implicit val timeout = akka.util.Timeout(1 second)

  override def route(implicit exchange: HttpServerExchange) = {
    case (Methods.GET, "parameters") =>
      val config = Config.get
      respondJson(exchange, Seq(
        JsObject("name" -> JsString("connectionTimeout") ::
          "value" -> JsNumber(config.connectionTimeout.toMillis) ::
          "unit" -> JsString("ms") :: Nil),
        JsObject("name" -> JsString("connectionIdleTimeout") ::
          "value" -> JsNumber(config.connectionIdleTimeout.toMillis) ::
          "unit" -> JsString("ms") :: Nil),
        JsObject("name" -> JsString("mainClientMaxConnections") ::
          "value" -> JsNumber(config.mainClientMaxConnections) :: Nil),
        JsObject("name" -> JsString("mainClientMaxConnectionsPerHost") ::
          "value" -> JsNumber(config.mainClientMaxConnectionsPerHost) :: Nil),
        JsObject("name" -> JsString("proxyClientMaxConnections") ::
          "value" -> JsNumber(config.proxyClientMaxConnections) :: Nil),
        JsObject("name" -> JsString("proxyClientMaxConnectionsPerHost") ::
          "value" -> JsNumber(config.proxyClientMaxConnectionsPerHost) :: Nil),
        JsObject("name" -> JsString("headRetryTimes") :: "value" -> JsNumber(config.headRetryTimes) :: Nil),
        JsObject("name" -> JsString("headTimeout") :: "value" -> JsNumber(config.headTimeout.toSeconds) :: "unit" -> JsString("s") :: Nil),
        JsObject("name" -> JsString("getDataTimeout") :: "value" -> JsNumber(config.getDataTimeout.toSeconds) :: "unit" -> JsString("s") :: Nil)
      ))
    case (Methods.PUT, "connectionTimeout") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
      setConfigAndRespond(exchange,
        Repox.configPersister ? SetConnectionTimeout(Duration.apply(newV.toLong, MILLISECONDS)))
    case (Methods.PUT, "connectionIdleTimeout") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
      setConfigAndRespond(exchange,
        Repox.configPersister ? SetConnectionIdleTimeout(Duration.apply(newV.toLong, MILLISECONDS)))
    case (Methods.PUT, "mainClientMaxConnections") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
      setConfigAndRespond(exchange,
        Repox.configPersister ? SetMainClientMaxConnections(newV.toInt))
    case (Methods.PUT, "mainClientMaxConnectionsPerHost") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
      setConfigAndRespond(exchange,
        Repox.configPersister ? SetMainClientMaxConnectionsPerHost(newV.toInt))
    case (Methods.PUT, "proxyClientMaxConnections") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
      setConfigAndRespond(exchange,
        Repox.configPersister ? SetProxyClientMaxConnections(newV.toInt))
    case (Methods.PUT, "proxyClientMaxConnectionsPerHost") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
      setConfigAndRespond(exchange,
        Repox.configPersister ? SetProxyClientMaxConnectionsPerHost(newV.toInt))
    case (Methods.PUT, "headTimeout") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
      setConfigAndRespond(exchange,
        Repox.configPersister ? SetHeadTimeout(Duration.apply(newV.toInt, SECONDS)))
    case (Methods.PUT, "getDataTimeout") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
      setConfigAndRespond(exchange,
        Repox.configPersister ? SetGetDataTimeout(Duration.apply(newV.toInt, SECONDS)))
    case (Methods.PUT, "headRetryTimes") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
      setConfigAndRespond(exchange,
        Repox.configPersister ? SetHeadRetryTimes(newV.toInt))
  }
}
