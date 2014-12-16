package com.gtan.repox.admin

import java.net.URLDecoder

import akka.pattern.ask
import com.gtan.repox.Repox
import com.gtan.repox.config.{Config, ConfigPersister}
import com.gtan.repox.data.Connector
import io.undertow.server.HttpServerExchange
import io.undertow.util.{HttpString, Methods}
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.duration._
import scala.language.postfixOps

object ConnectorsHandler extends RestHandler {

  import com.gtan.repox.admin.WebConfigHandler._
  import com.gtan.repox.config.ConfigPersister._

  implicit val timeout = akka.util.Timeout(1 second)

  override def route(implicit exchange: HttpServerExchange): PartialFunction[(HttpString, String), Unit] = {
    case (Methods.GET, "connectors") =>
      val config = Config.get
      respondJson(exchange, JsObject(
        "connectors" -> Json.toJson(config.connectors.map(ConnectorVO.wrap)) ::
        "proxies" -> Json.toJson(config.proxies) ::
        Nil
      ))
    case (Methods.POST, "connector") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
      val vo = Json.parse(newV).as[ConnectorVO]
      setConfigAndRespond(exchange, Repox.configPersister ? NewConnector(vo))
    case (Methods.PUT, "connector") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
      val vo = Json.parse(newV).as[ConnectorVO]
      setConfigAndRespond(exchange, Repox.configPersister ? UpdateConnector(vo))
    case (Methods.DELETE, "connector") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
      setConfigAndRespond(exchange, Repox.configPersister ? DeleteConnector(newV.toLong))

  }
}
