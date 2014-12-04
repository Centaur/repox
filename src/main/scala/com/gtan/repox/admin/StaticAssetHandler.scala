package com.gtan.repox.admin

import io.undertow.Handlers
import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.resource.ClassPathResourceManager
import io.undertow.util.Methods

object StaticAssetHandler extends RestHandler {

  import WebConfigHandler._

  override def route(implicit exchange: HttpServerExchange) = {
    case (Methods.GET, target) if isStaticRequest(target) =>
      Handlers
        .resource(new ClassPathResourceManager(this.getClass.getClassLoader))
        .handleRequest(exchange)

  }
}
