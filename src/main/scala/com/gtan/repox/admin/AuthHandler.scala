package com.gtan.repox.admin

import java.io.IOException
import java.nio.charset.Charset
import java.util.Date

import com.google.common.base.Charsets
import com.gtan.repox.Repox
import com.gtan.repox.config.ConfigPersister.SaveSnapshot
import com.gtan.repox.config._
import com.typesafe.scalalogging.LazyLogging
import io.undertow.io.Receiver.{ErrorCallback, FullStringCallback}
import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.{CookieImpl, Cookie}
import io.undertow.util._
import play.api.libs.json.Json
import akka.pattern.ask
import concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}
import concurrent.ExecutionContext.Implicits.global

object AuthHandler extends RestHandler with LazyLogging with ConfigFormats {

  import WebConfigHandler._
  import ParameterPersister._

  implicit val timeout = akka.util.Timeout(1 second)

  val AUTH_KEY: String = "authenticated"

  private def authenticated(exchange: HttpServerExchange) = {
    val cookie = exchange.getRequestCookies.get(AUTH_KEY)
    cookie != null && cookie.getValue == "true"
  }

  override def route(implicit exchange: HttpServerExchange) = {
    val globallyAccessible: PartialFunction[(HttpString, String), Unit] = {
      case (Methods.POST, "login") =>
        val pass = exchange.getQueryParameters.get("v").getFirst
        exchange.setStatusCode(StatusCodes.OK)
        if (Config.password == pass) {
          exchange.setResponseCookie(new CookieImpl(AUTH_KEY, "true").setPath("/admin"))
          exchange.getResponseSender.send( """{"success": true}""")
        } else {
          exchange.getResponseSender.send( """{"success": false}""")
        }
      case (Methods.POST, "logout") =>
        exchange.setStatusCode(StatusCodes.OK)
        exchange.setResponseCookie(new CookieImpl("authenticated", "true").setPath("/admin").setMaxAge(0))
        exchange.getRequestCookies.remove("authenticated")
        exchange.getResponseChannel
        exchange.endExchange()
      case (Methods.GET, "exportConfig") =>
        exchange.setStatusCode(StatusCodes.OK)
        exchange.getResponseHeaders.add(Headers.CONTENT_TYPE, "application/force-download")
        exchange.getResponseHeaders.add(Headers.CONTENT_DISPOSITION, """attachment; filename="repox.config.json""")
        exchange.getResponseSender.send(Json.toJson(Config.get.copy(password = "not exported")).toString)
    }
    val needAuth: PartialFunction[(HttpString, String), Unit] = {
      case _ if !authenticated(exchange) =>
        exchange.setStatusCode(StatusCodes.FORBIDDEN)
        exchange.endExchange()
    }
    globallyAccessible orElse needAuth orElse {
      case (Methods.POST, "saveSnapshot") =>
        (Repox.configPersister ? SaveSnapshot).onComplete {
          result =>
            exchange.getResponseSender.send( s"""{"success": ${result.isSuccess}}""")
        }
      case (Methods.PUT, "importConfig") =>
        val contentType = exchange.getRequestHeaders.getFirst(Headers.CONTENT_TYPE)
        if (contentType.startsWith("application/json")) {
          val splitted = contentType.split("charset=")
          val charset = if(splitted.length == 2) Charset.forName(splitted(1)) else Charsets.UTF_8
          exchange.getRequestReceiver.receiveFullString(
            new FullStringCallback {
              override def handle(exchange: HttpServerExchange, message: String): Unit = {
                val uploaded = Json.parse(message).as[Config]
                setConfigAndRespond(exchange, Repox.configPersister ? ImportConfig(uploaded))
              }
            }, new ErrorCallback {
              override def error(exchange: HttpServerExchange, e: IOException): Unit = {
                exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR)
                exchange.endExchange()
              }
            }, charset)
        } else {
          exchange.setStatusCode(StatusCodes.BAD_REQUEST)
          exchange.endExchange()
        }
      case (Methods.PUT, "password") =>
        val v = exchange.getQueryParameters.get("v").getFirst
        val json = Json.parse(v)
        val (p1, p2) = ((json \ "p1").as[String], (json \ "p2").as[String])
        if (p1 == p2) {
          setConfigAndRespond(exchange, Repox.configPersister ? ModifyPassword(p1))
        }
    }
  }
}