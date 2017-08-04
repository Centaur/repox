package com.gtan.repox

import com.gtan.repox.admin.{AuthHandler, ConnectorVO, RepoVO, WebConfigHandler}
import com.gtan.repox.config.ConnectorPersister.{DeleteConnector, NewConnector, UpdateConnector}
import com.gtan.repox.config.ExpireRulePersister.{DeleteExpireRule, DisableExpireRule, EnableExpireRule, NewOrUpdateExpireRule}
import com.gtan.repox.config.Immediate404RulePersister.{DeleteImmediate404Rule, DisableImmediate404Rule, EnableImmediate404Rule, NewOrUpdateImmediate404Rule}
import com.gtan.repox.config.ParameterPersister.{ModifyPassword, SetExtraResources, SetHeadRetryTimes, SetHeadTimeout}
import com.gtan.repox.config.ProxyPersister.{DeleteProxy, DisableProxy, EnableProxy, NewOrUpdateProxy}
import com.gtan.repox.config.RepoPersister.{DeleteRepo, DisableRepo, EnableRepo, MoveDownRepo, MoveUpRepo, NewRepo, UpdateRepo}
import com.gtan.repox.config.{Config, ConfigFormats, ImportConfig}
import com.gtan.repox.data.{ExpireRule, Immediate404Rule, ProxyServer}
import io.circe.{Decoder, Json}
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl._
import org.http4s.headers.{`Content-Disposition`, `Content-Type`}
import org.http4s.server.syntax._

import scala.util.Try
import scalaz.ValidationNel
import scalaz.concurrent.Task


object New extends ConfigFormats {

  import akka.pattern.ask
  import delorean._

  import concurrent.ExecutionContext.Implicits.global
  import concurrent.duration._

  implicit val timeout = akka.util.Timeout(1 second)

  val Admin: Path = Root / "admin"

  object QsParamV extends QueryParamDecoderMatcher[String]("v")

  implicit def jsonQueryParamDecoder[T: Decoder] = new QueryParamDecoder[T] {
    override def decode(value: QueryParameterValue): ValidationNel[ParseFailure, T] =
      QueryParamDecoder.decodeBy[T, String](str => Json.fromString(str).as[T].right.get)
      .decode(value)
  }

  object QsParamVAsRepoVO extends QueryParamDecoderMatcher[RepoVO]("v")

  object QsParamVAsConnectorVO extends QueryParamDecoderMatcher[ConnectorVO]("v")

  object QsParamVAsProxyServer extends QueryParamDecoderMatcher[ProxyServer]("v")

  object QsParamVAsImmediate404Rule extends QueryParamDecoderMatcher[Immediate404Rule]("v")

  object QsParamVAsExpireRule extends QueryParamDecoderMatcher[ExpireRule]("v")

  object QsParamVAsId extends QueryParamDecoderMatcher[Long]("v")

  object QsParamVAsInt extends QueryParamDecoderMatcher[Int]("v")

  val staticAssetService = HttpService {
    case request@GET -> "admin" /: suburi if WebConfigHandler.isStaticRequest(suburi.toString) =>
      StaticFile.fromResource(suburi.toString, Some(request))
      .map(Task.now)
      .getOrElse(NotFound())
  }
  val authService = HttpService {
    case POST -> Admin / "login" :? QsParamV(pass) =>
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
    case request if !request.headers.get(headers.Cookie)
                     .exists(_.values.exists(cookie => cookie.name == AuthHandler.AUTH_KEY && cookie.content == "true")) =>
      Forbidden()
    case request@PUT -> Admin / "importConfig" =>
      Try {
        val contentType = request.headers.get(headers.`Content-Type`)
        require(contentType.exists(_.mediaType == MediaType.`application/json`))
        for {
          uploaded <- request.as(jsonOf[Config])
          _ <- (Repox.configPersister ? ImportConfig(uploaded)).toTask
          response <- NoContent()
        } yield response
      } getOrElse BadRequest()
    case PUT -> Admin / "password" :? QsParamV(payload) =>
      Try {
        val json = Json.fromString(payload)
        val (Right(p1), Right(p2)) = (json.hcursor.get[String]("p1"), json.hcursor.get[String]("p2"))
        require(p1 == p2)
        for {
          _ <- (Repox.configPersister ? ModifyPassword(p1)).toTask
          response <- NoContent()
        } yield response
      } getOrElse BadRequest()
  }

  def simpleCommand[T](cmd: T => Any, payload: T): Task[Response] =
    Try {
      for {
        _ <- (Repox.configPersister ? cmd(payload)).toTask
        response <- NoContent()
      } yield response
    } getOrElse BadRequest()

  val upstreamsService = HttpService {
    case GET -> Admin / "upstreams" =>
      val config = Config.get
      Ok(Json.obj(
        "upstreams" -> config.repos.sortBy(_.priority).map(RepoVO.wrap).asJson,
        "connectors" -> config.connectors.filterNot(_.name == "default").asJson
      ))
    case POST -> Admin / "upstream" :? QsParamVAsRepoVO(vo) => simpleCommand(NewRepo, vo)
    case POST -> Admin / "upstream" / "up" :? QsParamVAsId(id) => simpleCommand(MoveUpRepo, id)
    case POST -> Admin / "upstream" / "down" :? QsParamVAsId(id) => simpleCommand(MoveDownRepo, id)
    case PUT -> Admin / "upstream" :? QsParamVAsRepoVO(vo) => simpleCommand(UpdateRepo, vo)
    case PUT -> Admin / "upstream" / "disable" :? QsParamVAsId(id) => simpleCommand(DisableRepo, id)
    case PUT -> Admin / "upstream" / "enable" :? QsParamVAsId(id) => simpleCommand(EnableRepo, id)
    case DELETE -> Admin / "upstream" :? QsParamVAsId(id) => simpleCommand(DeleteRepo, id)
  }

  val connectorsService = HttpService {
    case GET -> Admin / "connectors" =>
      val config = Config.get
      Ok(Json.obj(
        "connectors" -> config.connectors.map(ConnectorVO.wrap).asJson,
        "proxies" -> config.proxies.asJson,
      ))
    case POST -> Admin / "connector" :? QsParamVAsConnectorVO(vo) => simpleCommand(NewConnector, vo)
    case PUT -> Admin / "connector" :? QsParamVAsConnectorVO(vo) => simpleCommand(UpdateConnector, vo)
    case DELETE -> Admin / "connector" :? QsParamVAsId(id) => simpleCommand(DeleteConnector, id)
  }

  val proxiesService = HttpService {
    case GET -> Admin / "proxies" => Ok(Config.proxies.asJson)
    case (POST | PUT) -> Admin / "proxy" :? QsParamVAsProxyServer(proxy) => simpleCommand(NewOrUpdateProxy, proxy)
    case PUT -> Admin / "proxy" / "enable" :? QsParamVAsId(id) => simpleCommand(EnableProxy, id)
    case PUT -> Admin / "proxy" / "disable" :? QsParamVAsId(id) => simpleCommand(DisableProxy, id)
    case DELETE -> Admin / "proxy" :? QsParamVAsId(id) => simpleCommand(DeleteProxy, id)
  }

  val immediate404RulesService = HttpService {
    case GET -> Admin / "immediate404Rules" => Ok(Config.immediate404Rules.asJson)
    case (POST | PUT) -> Admin / "immediate404Rules" :? QsParamVAsImmediate404Rule(rule) => simpleCommand(NewOrUpdateImmediate404Rule, rule)
    case PUT -> Admin / "immediate404Rules" / "enable" :? QsParamVAsId(id) => simpleCommand(EnableImmediate404Rule, id)
    case PUT -> Admin / "immediate404Rules" / "disable" :? QsParamVAsId(id) => simpleCommand(DisableImmediate404Rule, id)
    case DELETE -> Admin / "immediate404Rules" :? QsParamVAsId(id) => simpleCommand(DeleteImmediate404Rule, id)
  }

  val expireRulesService = HttpService {
    case GET -> Admin / "expireRules" => Ok(Config.expireRules.asJson)
    case (POST | PUT) -> Admin / "expireRules" :? QsParamVAsExpireRule(rule) => simpleCommand(NewOrUpdateExpireRule, rule)
    case PUT -> Admin / "expireRules" / "enable" :? QsParamVAsId(id) => simpleCommand(EnableExpireRule, id)
    case PUT -> Admin / "expireRules" / "disable" :? QsParamVAsId(id) => simpleCommand(DisableExpireRule, id)
    case DELETE -> Admin / "expireRules" :? QsParamVAsId(id) => simpleCommand(DeleteExpireRule, id)
  }

  val parametersService = HttpService {
    case PUT -> Admin / "headTimeout" :? QsParamVAsInt(headTimeout) =>
      simpleCommand((timeout: Int) => SetHeadTimeout(Duration(timeout, SECONDS)), headTimeout)
    case PUT -> Admin / "headRetryTimes" :? QsParamVAsInt(retryTimes) => simpleCommand(SetHeadRetryTimes, retryTimes)
    case PUT -> Admin / "extraResources" :? QsParamV(resources) => simpleCommand(SetExtraResources, resources)
  }

  val resetService = HttpService {
    case POST -> Admin / "resetMainClient" => NoContent()
    case POST -> Admin / "resetProxyClients" => NoContent()
    case _ => NotFound()
  }

  val service = List(staticAssetService,
    authService,
    upstreamsService,
    connectorsService,
    proxiesService,
    immediate404RulesService,
    expireRulesService,
    parametersService,
    resetService
  ).reduce(_ orElse _)
}
