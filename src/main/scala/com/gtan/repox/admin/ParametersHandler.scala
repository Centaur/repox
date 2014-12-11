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
        JsObject("name" -> JsString("headRetryTimes") :: "value" -> JsNumber(config.headRetryTimes) :: Nil),
        JsObject("name" -> JsString("headTimeout") :: "value" -> JsNumber(config.headTimeout.toSeconds) :: "unit" -> JsString("s") :: Nil)
      ))
    case (Methods.PUT, "headTimeout") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
      setConfigAndRespond(exchange,
        Repox.configPersister ? SetHeadTimeout(Duration.apply(newV.toInt, SECONDS)))
    case (Methods.PUT, "headRetryTimes") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
      setConfigAndRespond(exchange,
        Repox.configPersister ? SetHeadRetryTimes(newV.toInt))
  }
}
