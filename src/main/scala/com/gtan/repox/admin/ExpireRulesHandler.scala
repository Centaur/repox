package com.gtan.repox.admin

import java.net.URLDecoder

import com.gtan.repox.Repox
import com.gtan.repox.config.Config
import com.gtan.repox.data.ExpireRule
import io.undertow.server.HttpServerExchange
import io.undertow.util.Methods
import play.api.libs.json.Json
import akka.pattern.ask
import concurrent.duration._
import com.gtan.repox.config.ExpireRulePersister._

import collection.JavaConverters._

object ExpireRulesHandler extends RestHandler {
  implicit val timeout = akka.util.Timeout(5 seconds)

  import com.gtan.repox.admin.WebConfigHandler._

  override def route(implicit exchange: HttpServerExchange) = {
    case (Methods.GET, "expireRules") =>
      respondJson(exchange, Config.expireRules)
    case (Methods.POST, "expireRule") | (Methods.PUT, "expireRule") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
      val rule = Json.parse(newV).as[ExpireRule]
      setConfigAndRespond(exchange, Repox.configPersister ? NewOrUpdateExpireRule(rule))
    case (Methods.PUT, "expireRule/enable") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
      setConfigAndRespond(exchange, Repox.configPersister ? EnableExpireRule(newV.toLong))
    case (Methods.PUT, "expireRule/disable") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
      setConfigAndRespond(exchange, Repox.configPersister ? DisableExpireRule(newV.toLong))
    case (Methods.DELETE, "expireRule") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
      setConfigAndRespond(exchange, Repox.configPersister ? DeleteExpireRule(newV.toLong))

  }
}
