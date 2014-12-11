package com.gtan.repox.admin

import com.gtan.repox.Repox
import com.typesafe.scalalogging.LazyLogging
import io.undertow.server.HttpServerExchange
import io.undertow.util.Methods

object ResetHandler extends RestHandler with LazyLogging{
  import WebConfigHandler._

  override def route(implicit exchange: HttpServerExchange) = {
    case (Methods.POST, "resetMainClient") =>
//      setConfigAndRespond(exchange, Repox.mainClient.alter(Repox.createMainClient))
    case (Methods.POST, "resetProxyClients") =>
//      setConfigAndRespond(exchange, Repox.proxyClients.alter(Repox.createProxyClients))
    case _ =>
      logger.debug(s"Invalid Request. ${exchange.getRequestURI}")
      Repox.respond404(exchange)
  }
}
