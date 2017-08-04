package com.gtan.repox


import java.util.concurrent.{ExecutorService, Executors}

import com.gtan.repox.admin.WebConfigHandler
import io.undertow.predicate.Predicates
import io.undertow.server.handlers.{PredicateContextHandler, PredicateHandler}
import io.undertow.server.{HttpHandler, HttpServerExchange}
import io.undertow.{Undertow, UndertowOptions}
import org.http4s.server.ServerApp
import org.xnio.Options

import scala.util.Properties.envOrNone
import scalaz.concurrent.Task

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


object Http4sMain extends ServerApp {

  import org.http4s.server.Server
  import org.http4s.server.blaze.BlazeBuilder

  val port: Int = envOrNone("HTTP_PORT") map (_.toInt) getOrElse 8078
  val ip: String = "0.0.0.0"
  val pool: ExecutorService = Executors.newCachedThreadPool()

  override def server(args: List[String]): Task[Server] =
    BlazeBuilder
      .bindHttp(port, ip)
      .mountService(New.service)
      .withServiceExecutor(pool)
      .start
}
