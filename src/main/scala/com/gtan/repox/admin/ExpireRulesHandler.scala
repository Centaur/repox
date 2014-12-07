package com.gtan.repox.admin

import com.gtan.repox.config.Config
import io.undertow.server.HttpServerExchange
import io.undertow.util.Methods

import collection.JavaConverters._

object ExpireRulesHandler extends RestHandler {

  import WebConfigHandler._

  override def route(implicit exchange: HttpServerExchange) = {
    case (Methods.GET, "expireRules") =>
      respondJson(exchange, Config.expireRules)
    case (Methods.POST, "expireRule") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
    case (Methods.PUT, "expireRule") =>
      val newV = exchange.getQueryParameters.get("v").getFirst

  }
}
