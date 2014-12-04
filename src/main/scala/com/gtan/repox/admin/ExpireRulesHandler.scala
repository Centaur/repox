package com.gtan.repox.admin

import io.undertow.server.HttpServerExchange
import io.undertow.util.Methods

object ExpireRulesHandler extends RestHandler {

  import WebConfigHandler._

  override def route(implicit exchange: HttpServerExchange) = {
    case (Methods.GET, "expireRules") =>
    //        respondJson(httpServerExchange, Config.expireRules)
    case (Methods.POST, "expireRule") =>
    case (Methods.PUT, "expireRule") =>
  }
}
