package com.gtan.repox.admin

import akka.pattern.ask
import com.gtan.repox.Repox
import com.gtan.repox.config.Config
import com.gtan.repox.config.RepoPersister._
import io.undertow.server.HttpServerExchange
import io.undertow.util.{HttpString, Methods}
import play.api.libs.json.Json

import scala.concurrent.duration._
import scala.language.postfixOps

object UpstreamsHandler extends RestHandler {

  import WebConfigHandler._

  implicit val timeout = akka.util.Timeout(1 second)

  override def route(implicit exchange: HttpServerExchange): PartialFunction[(HttpString, String), Unit] = {
    case (Methods.GET, "upstreams") =>
      val config = Config.get
      respondJson(exchange, Json.obj(
        "upstreams" -> config.repos.sortBy(_.priority).map(RepoVO.wrap),
        "connectors" -> config.connectors.filterNot(_.name == "default")
      ))

    case (Methods.POST, "upstream") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
      val vo = Json.parse(newV).as[RepoVO]
      setConfigAndRespond(exchange, Repox.configPersister ? NewRepo(vo))
    case (Methods.POST, "upstream/up") =>
      val id = exchange.getQueryParameters.get("v").getFirst.toLong
      setConfigAndRespond(exchange, Repox.configPersister ? MoveUpRepo(id))
    case (Methods.POST, "upstream/down") =>
      val id = exchange.getQueryParameters.get("v").getFirst.toLong
      setConfigAndRespond(exchange, Repox.configPersister ? MoveDownRepo(id))
    case (Methods.PUT, "upstream") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
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
