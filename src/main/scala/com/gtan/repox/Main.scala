package com.gtan.repox

import com.gtan.repox.admin.WebConfigHandler
import io.undertow.Undertow
import io.undertow.predicate.{Predicates, Predicate}
import io.undertow.server.handlers.{PredicateContextHandler, PredicateHandler}
import io.undertow.server.{HttpServerExchange, HttpHandler}

object Main {
  def httpHandlerBridge(realHandler: HttpServerExchange => Unit): HttpHandler = new HttpHandler() {
    override def handleRequest(exchange: HttpServerExchange) = {
      exchange.dispatch(scala.concurrent.ExecutionContext.Implicits.global, new Runnable {
        override def run(): Unit = realHandler.apply(exchange)
      })
    }
  }

  def main(args: Array[String]) {
    Repox.init()
    val server: Undertow = Undertow.builder
      .addHttpListener(8078, "0.0.0.0")
      .setHandler(
        new PredicateContextHandler(
          new PredicateHandler(
            Predicates.prefix("/admin/"),
            httpHandlerBridge(WebConfigHandler.handle),
            httpHandlerBridge(Repox.handle)
          ))
      ).build
    server.start()
  }

}
