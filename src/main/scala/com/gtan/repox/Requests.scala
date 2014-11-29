package com.gtan.repox
import io.undertow.server.HttpServerExchange

object Requests {
  trait Request
  case class Get(exchange: HttpServerExchange) extends Request
  case class Head(exchange: HttpServerExchange) extends Request
  case class Download(uri: String, from: Repo) extends Request
}
