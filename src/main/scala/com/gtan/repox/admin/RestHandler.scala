package com.gtan.repox.admin

import io.undertow.server.HttpServerExchange
import io.undertow.util.HttpString

trait RestHandler {
  def route(implicit exchange: HttpServerExchange): PartialFunction[(HttpString, String), Unit]
}
