package com.gtan.repox

import com.gtan.repox.admin.WebConfigHandler
import io.undertow.Undertow
import io.undertow.predicate.{Predicates, Predicate}
import io.undertow.server.handlers.{PredicateContextHandler, PredicateHandler}
import io.undertow.server.{HttpServerExchange, HttpHandler}

/**
 * Created by xf on 14/11/20.
 */
object Main {
  def main(args: Array[String]) {
    Repox.init()
    val server: Undertow = Undertow.builder
      .addHttpListener(8078, "0.0.0.0")
      .setHandler(
        new PredicateContextHandler(
          new PredicateHandler(
            Predicates.prefix("/admin/"),
            new WebConfigHandler(),
            new HttpHandler() {
              override def handleRequest(httpServerExchange: HttpServerExchange): Unit = {
                httpServerExchange.dispatch(scala.concurrent.ExecutionContext.Implicits.global, new Runnable {
                  override def run(): Unit = Repox.handle(httpServerExchange)
                })
              }
            }))
      ).build
    server.start()
  }

}
