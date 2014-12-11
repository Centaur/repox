package com.gtan.repox.admin

import com.gtan.repox.Repox
import io.undertow.server.HttpServerExchange
import io.undertow.util.Methods

object ResetHandler extends RestHandler{
  import WebConfigHandler._

  override def route(implicit exchange: HttpServerExchange) = {
    case (Methods.POST, "resetMainClient") =>
//      setConfigAndRespond(exchange, Repox.mainClient.alter(Repox.createMainClient))
    case (Methods.POST, "resetProxyClients") =>
//      setConfigAndRespond(exchange, Repox.proxyClients.alter(Repox.createProxyClients))
  }
}
