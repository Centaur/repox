package com.gtan.repox.admin

import akka.pattern.ask
import com.gtan.repox.Repox
import com.gtan.repox.config.Config
import io.undertow.server.HttpServerExchange
import io.undertow.util.{HttpString, Methods}

import scala.concurrent.duration._
import scala.language.postfixOps
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

object ConnectorsHandler extends RestHandler {

  import com.gtan.repox.admin.WebConfigHandler._
  import com.gtan.repox.config.ConnectorPersister._
  import com.gtan.repox.CirceCodecs.{durationDecoder, durationEncoder, protocolEncoder, protocolDecoder}

  implicit val timeout = akka.util.Timeout(1 second)

  override def route(implicit exchange: HttpServerExchange): PartialFunction[(HttpString, String), Unit] = {
    case (Methods.GET, "connectors") =>
      val config = Config.get
      respondJson(exchange, Json.obj(
        "connectors" -> config.connectors.map(ConnectorVO.wrap).asJson,
        "proxies" -> config.proxies.asJson)
      )
    case (Methods.POST, "connector") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
      decode[ConnectorVO](newV).fold(
        throw _, vo => setConfigAndRespond(exchange, Repox.configPersister ? NewConnector(vo))
      )
    case (Methods.PUT, "connector") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
      decode[ConnectorVO](newV).fold(
        throw _, vo => setConfigAndRespond(exchange, Repox.configPersister ? UpdateConnector(vo))
      )
    case (Methods.DELETE, "connector") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
      setConfigAndRespond(exchange, Repox.configPersister ? DeleteConnector(newV.toLong))

  }
}
