package com.gtan.repox.admin

import java.util.Date

import com.gtan.repox.Repox
import com.gtan.repox.config.{ParameterPersister, ConfigPersister, Config}
import com.typesafe.scalalogging.LazyLogging
import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.{CookieImpl, Cookie}
import io.undertow.util.{Cookies, StatusCodes, Methods}
import play.api.libs.json.Json
import akka.pattern.ask
import concurrent.duration._
import scala.language.postfixOps

object AuthHandler extends RestHandler with LazyLogging {

  import WebConfigHandler._
  import ParameterPersister._

  implicit val timeout = akka.util.Timeout(1 second)


  override def route(implicit exchange: HttpServerExchange) = {
    case (Methods.POST, "login") =>
      val pass = exchange.getQueryParameters.get("v").getFirst
      exchange.setStatusCode(StatusCodes.OK)
      if (Config.password == pass) {
        exchange.setResponseCookie(new CookieImpl("authenticated", "true").setPath("/admin"))
        exchange.getResponseSender.send("""{"success": true}""")
      } else {
        exchange.getResponseSender.send("""{"success": false}""")
      }
    case (Methods.POST, "logout") =>
      exchange.setStatusCode(StatusCodes.OK)
      exchange.setResponseCookie(new CookieImpl("authenticated", "true").setPath("/admin").setMaxAge(0))
      exchange.getRequestCookies.remove("authenticated")
      exchange.getResponseChannel
      exchange.endExchange()
    case (Methods.PUT, "password") =>
      val v = exchange.getQueryParameters.get("v").getFirst
      val json = Json.parse(v)
      val (p1, p2) = ((json \ "p1").as[String], (json \ "p2").as[String])
      if (p1 == p2) {
        setConfigAndRespond(exchange, Repox.configPersister ? ModifyPassword(p1))
      }
  }
}
