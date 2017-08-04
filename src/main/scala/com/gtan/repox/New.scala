package com.gtan.repox

import com.gtan.repox.admin.{AuthHandler, WebConfigHandler}
import io.circe.Json
import org.http4s._
import org.http4s.dsl._
import org.http4s.circe._
import org.http4s.server._
import io.circe.generic.auto._
import io.circe.syntax._

import scalaz.concurrent.Task
import config.{Config, ConfigFormats}
import org.http4s.headers.{`Content-Disposition`, `Content-Type`}


object New extends ConfigFormats {

  val Admin: Path = Root / "admin"

  def QS(name: String) = new QueryParamDecoderMatcher[String](name) {}

  val service = HttpService {
    case request@GET -> "admin" /: suburi if WebConfigHandler.isStaticRequest(suburi.toString) =>
      StaticFile.fromResource(suburi.toString, Some(request))
        .map(Task.now)
        .getOrElse(NotFound())
    case request@POST -> Admin / "login" =>
      val pass: String = request.params("v")
      if (Config.password == pass) {
        Ok(Json.obj("success" -> Json.fromBoolean(true))).addCookie(
          Cookie(AuthHandler.AUTH_KEY, "true").copy(path = Some("/admin"))
        )
      } else {
        Ok(Json.obj("success" -> Json.fromBoolean(false)))
      }
    case POST -> Admin / "logout" =>
      NoContent().removeCookie(AuthHandler.AUTH_KEY)
    case GET -> Admin / "exportConfig" =>
      Ok(Config.get.copy(password = "not exported").asJson).putHeaders(
        `Content-Type`(MediaType.fromKey("application" -> "force-download")),
        `Content-Disposition`("attachment", Map("filename" -> "repox.config.json"))
      )
  }
}
