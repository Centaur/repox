package com.gtan.repox

import io.undertow.Undertow
import io.undertow.server.{HttpServerExchange, HttpHandler}

/**
 * Created by xf on 14/11/20.
 */
object Main {
  def main(args: Array[String]) {
    val server: Undertow = Undertow.builder
      .addHttpListener(8078, "localhost")
      .setHandler(new HttpHandler() {
      override def handleRequest(httpServerExchange: HttpServerExchange): Unit = {
        httpServerExchange.dispatch(scala.concurrent.ExecutionContext.Implicits.global, new Runnable {
          override def run(): Unit = Repox.handle(httpServerExchange)
        })
      }
    }).build
    server.start()
  }

}
