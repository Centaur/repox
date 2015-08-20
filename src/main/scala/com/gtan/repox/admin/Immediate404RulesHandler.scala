package com.gtan.repox.admin

import java.net.URLDecoder

import com.gtan.repox.Repox
import com.gtan.repox.config.Config
import com.gtan.repox.config.Immediate404RulePersister._
import com.gtan.repox.data.Immediate404Rule
import io.undertow.server.HttpServerExchange
import io.undertow.util.Methods
import play.api.libs.json.Json
import akka.pattern.ask
import concurrent.duration._

object Immediate404RulesHandler extends RestHandler {
  implicit val timeout = akka.util.Timeout(5 seconds)

  import com.gtan.repox.admin.WebConfigHandler._

  override def route(implicit exchange: HttpServerExchange) = {
    case (Methods.GET, "immediate404Rules") =>
      respondJson(exchange, Config.immediate404Rules)
    case (Methods.POST, "immediate404Rule") | (Methods.PUT, "immediate404Rule") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
      val rule = Json.parse(newV).as[Immediate404Rule]
      setConfigAndRespond(exchange, Repox.configPersister ? NewOrUpdateImmediate404Rule(rule))
    case (Methods.PUT, "immediate404Rule/enable") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
      setConfigAndRespond(exchange, Repox.configPersister ? EnableImmediate404Rule(newV.toLong))
    case (Methods.PUT, "immediate404Rule/disable") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
      setConfigAndRespond(exchange, Repox.configPersister ? DisableImmediate404Rule(newV.toLong))
    case (Methods.DELETE, "immediate404Rule") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
      setConfigAndRespond(exchange, Repox.configPersister ? DeleteImmediate404Rule(newV.toLong))

  }
}
