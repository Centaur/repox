package com.gtan.repox.admin

import com.gtan.repox.Repox
import com.gtan.repox.config.{ConfigPersister, Config}
import io.undertow.server.HttpServerExchange
import io.undertow.util.{Methods, HttpString}
import collection.JavaConverters._
import akka.pattern.ask
import concurrent.duration._

object UpstreamsHandler extends RestHandler {

  import WebConfigHandler._
  import ConfigPersister._

  implicit val timeout = akka.util.Timeout(1 second)

  override def route(implicit exchange: HttpServerExchange): PartialFunction[(HttpString, String), Unit] = {
    case (Methods.GET, "upstreams") =>
      val config = Config.get
      respondJson(exchange, Map(
        "upstreams" -> config.repos.sortBy(_.id).map(RepoVO.apply(_).toMap).asJava,
        "proxies" -> config.proxies.map(_.toMap).asJava
      ).asJava)

    case (Methods.POST, "upstream") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
      val vo = RepoVO.fromJson(newV)
      setConfigAndRespond(exchange, Repox.configPersister ? NewRepo(vo))

    case (Methods.PUT, "upstream") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
      val vo = RepoVO.fromJson(newV)
      setConfigAndRespond(exchange, Repox.configPersister ? UpdateRepo(vo))

    case (Methods.DELETE, "upstream") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
      val vo = RepoVO.fromJson(newV)
      setConfigAndRespond(exchange, Repox.configPersister ? DeleteRepo(vo))
  }
}
