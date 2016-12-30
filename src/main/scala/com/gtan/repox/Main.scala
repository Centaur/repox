package com.gtan.repox


import com.gtan.repox.admin.WebConfigHandler
import io.undertow.predicate.Predicates
import io.undertow.server.handlers.{PredicateContextHandler, PredicateHandler}
import io.undertow.server.{HttpHandler, HttpServerExchange}
import io.undertow.{Undertow, UndertowOptions}
import org.xnio.Options

object Main {
  def httpHandlerBridge(realHandler: HttpServerExchange => Unit): HttpHandler = new HttpHandler() {
    override def handleRequest(exchange: HttpServerExchange): Unit = {
      exchange.dispatch(scala.concurrent.ExecutionContext.Implicits.global.execute, () => realHandler.apply(exchange))
    }
  }

  def main(args: Array[String]) {
    Repox.init()
    val server: Undertow = Undertow.builder
      .addHttpListener(8078, "0.0.0.0")
      .setServerOption[Integer](UndertowOptions.IDLE_TIMEOUT, 1000 * 60 * 30)
      .setSocketOption[java.lang.Boolean](Options.KEEP_ALIVE, true)
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
