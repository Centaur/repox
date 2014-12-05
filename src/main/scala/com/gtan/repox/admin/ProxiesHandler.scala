package com.gtan.repox.admin

import com.gtan.repox.config.Config
import io.undertow.server.HttpServerExchange
import io.undertow.util.{HttpString, Methods}
import collection.JavaConverters._

object ProxiesHandler extends RestHandler {

  import WebConfigHandler._

  override def route(implicit exchange: HttpServerExchange): PartialFunction[(HttpString, String), Unit] = {
    case (Methods.GET, "proxies") =>
      respondJson(exchange, Map(
        "proxies" -> Config.proxies.map(_.toMap).asJava
      ).asJava)
    case (Methods.POST, "proxy") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
    case (Methods.PUT, "proxy") =>
      val newV = exchange.getQueryParameters.get("v").getFirst

  }
}
