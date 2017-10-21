package com.gtan.repox


import java.util.concurrent.{Executor, ExecutorService, Executors}

import com.gtan.repox.admin.WebConfigHandler
import fs2.Task
import io.undertow.predicate.Predicates
import io.undertow.server.handlers.{PredicateContextHandler, PredicateHandler}
import io.undertow.server.{HttpHandler, HttpServerExchange}
import io.undertow.{Undertow, UndertowOptions}
import org.http4s.util.StreamApp
import org.xnio.Options

import scala.util.Properties.envOrNone

object Main {
  def httpHandlerBridge(realHandler: HttpServerExchange => Unit): HttpHandler = (exchange: HttpServerExchange) => {
    exchange.dispatch(
      scala.concurrent.ExecutionContext.Implicits.global.execute,
      () => realHandler.apply(exchange))
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


object Http4sMain extends StreamApp {

  import org.http4s.server.blaze.BlazeBuilder

  val port: Int = envOrNone("HTTP_PORT") map (_.toInt) getOrElse 8078
  val ip: String = "0.0.0.0"
  val pool: ExecutorService = Executors.newCachedThreadPool()

  override def stream(args: List[String]): fs2.Stream[Task, Nothing] =
    BlazeBuilder
      .bindHttp(port = port, host = ip)
      .mountService(New.service)
      .serve
}
