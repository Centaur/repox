package com.gtan.repox.admin

import java.net.URLDecoder

import com.gtan.repox.Repox
import com.gtan.repox.config.{ConfigPersister, Config}
import io.undertow.server.HttpServerExchange
import io.undertow.util.{Methods, HttpString}
import play.api.libs.json.{JsObject, Format, Json}
import collection.JavaConverters._
import akka.pattern.ask
import concurrent.duration._
import scala.language.postfixOps

object UpstreamsHandler extends RestHandler {

  import WebConfigHandler._
  import ConfigPersister._

  implicit val timeout = akka.util.Timeout(1 second)

  override def route(implicit exchange: HttpServerExchange): PartialFunction[(HttpString, String), Unit] = {
    case (Methods.GET, "upstreams") =>
      val config = Config.get
      respondJson(exchange, JsObject(
        "upstreams" -> Json.toJson(config.repos.map(RepoVO.wrap)) ::
        "connectors" -> Json.toJson(config.connectors.filterNot(_.name == "default")) ::
        Nil
      ))

    case (Methods.POST, "upstream") =>
      val newV = URLDecoder.decode(exchange.getQueryParameters.get("v").getFirst, "UTF-8")
      val vo = Json.parse(newV).as[RepoVO]
      setConfigAndRespond(exchange, Repox.configPersister ? NewRepo(vo))
    case (Methods.POST, "upstream/up") =>
      val id = exchange.getQueryParameters.get("v").getFirst.toLong
      setConfigAndRespond(exchange, Repox.configPersister ? MoveUpRepo(id))
    case (Methods.POST, "upstream/down") =>
      val id = exchange.getQueryParameters.get("v").getFirst.toLong
      setConfigAndRespond(exchange, Repox.configPersister ? MoveDownRepo(id))
    case (Methods.PUT, "upstream") =>
      val newV = URLDecoder.decode(exchange.getQueryParameters.get("v").getFirst, "UTF-8")
      val vo = Json.parse(newV).as[RepoVO]
      setConfigAndRespond(exchange, Repox.configPersister ? UpdateRepo(vo))
    case (Methods.PUT, "upstream/disable") =>
      val id = exchange.getQueryParameters.get("v").getFirst.toLong
      setConfigAndRespond(exchange, Repox.configPersister ? DisableRepo(id))
    case (Methods.PUT, "upstream/enable") =>
      val id = exchange.getQueryParameters.get("v").getFirst.toLong
      setConfigAndRespond(exchange, Repox.configPersister ? EnableRepo(id))
    case (Methods.DELETE, "upstream") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
      setConfigAndRespond(exchange, Repox.configPersister ? DeleteRepo(newV.toLong))
  }
}
