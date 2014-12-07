package com.gtan.repox.admin

import com.gtan.repox.config.Config
import io.undertow.server.HttpServerExchange
import io.undertow.util.Methods

import collection.JavaConverters._

object Immediate404RulesHandler extends RestHandler {

  import WebConfigHandler._

  override def route(implicit exchange: HttpServerExchange) = {
    case (Methods.GET, "immediate404Rules") =>
      respondJson(exchange, Config.immediate404Rules)
    case (Methods.POST, "immediate404Rule") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
    case (Methods.PUT, "immediate404Rule") =>
      val newV = exchange.getQueryParameters.get("v").getFirst

  }
}
