package com.gtan.repox.config

import com.gtan.repox.data.{Connector, DurationFormat, ProxyServer, Repo}
import io.circe.{Decoder, Encoder}
import play.api.libs.json._

trait ConfigFormats extends DurationFormat{
  implicit val connectorUsageFormat = new Format[Map[Repo, Connector]] {
    override def writes(o: Map[Repo, Connector]) = JsArray(o.map {
      case (repo, connector) => JsObject(
        Seq(
          "repo" -> Json.toJson(repo),
          "connector" -> Json.toJson(connector)
        )
      )
    }.toSeq)

    override def reads(json: JsValue): JsResult[Map[Repo, Connector]] = try {
      (json: @unchecked) match {
        case JsArray(values) =>
          JsSuccess(values.map { value =>
            (value: @unchecked) match {
              case obj: JsObject => (obj.fields: @unchecked) match {
                case Seq(
                ("repo", repoJsVal: JsValue),
                ("connector", connectorJsVal: JsValue)) =>
                  repoJsVal.as[Repo] -> connectorJsVal.as[Connector]
              }
            }
          } toMap)
      }
    } catch {
      case e: MatchError => JsError(s"Config.connectorUsage deserialize from json failed. $e")
    }
  }

  implicit val proxyUsageFormat = new Format[Map[Connector, ProxyServer]] {
    override def writes(o: Map[Connector, ProxyServer]) = JsArray(o.map {
      case (connector, proxy) => JsObject(
        Seq(
          "connector" -> Json.toJson(connector),
          "proxy" -> Json.toJson(proxy)
        )
      )
    }.toSeq)

    override def reads(json: JsValue): JsResult[Map[Connector, ProxyServer]] = try {
      (json: @unchecked) match {
        case JsArray(values) =>
          JsSuccess(values.map { value =>
            (value: @unchecked) match {
              case obj: JsObject => (obj.fields: @unchecked) match {
                case Seq(
                ("connector", connectorJsVal: JsValue),
                ("proxy", proxyJsVal: JsValue)) =>
                  connectorJsVal.as[Connector] -> proxyJsVal.as[ProxyServer]
              }
            }
          } toMap)
      }
    } catch {
      case e: MatchError => JsError(s"Config.proxyUsage deserialize from json failed. $e")
    }
  }

  implicit val format = Json.format[Config]

  import io.circe.syntax._
  import cats.syntax.either._
  import io.circe.generic.auto._

  implicit val connectorUsageEncoder: Encoder[Map[Repo, Connector]] = Encoder.encodeJson.contramap(o =>
    io.circe.Json.arr(o.map {
      case (repo, connector) => Map(
        "repo" -> repo.asJson,
        "connector" -> connector.asJson
      ).asJson
    }.toSeq: _*))
  implicit val connectorUsageDecoder: Decoder[Map[Repo, Connector]] = Decoder.decodeJson.emap(json =>
    Either.catchNonFatal(json.asArray.get.map { value: io.circe.Json =>
        val obj = value.asObject.get
        (obj("repo").get.as[Repo].right.get,
          obj("connector").get.as[Connector].right.get)
      } toMap).leftMap(t => s"Config.connectorUsage deserialize from json failed. $t")
  )
  implicit val proxyUsageEncoder: Encoder[Map[Connector, ProxyServer]] = Encoder.encodeJson.contramap(o =>
    io.circe.Json.arr(o.map {
      case (connector, proxy) => Map(
        "connector" -> connector.asJson,
        "proxy" -> proxy.asJson
      ).asJson
    }.toSeq: _*))
  implicit val proxyUsageDecoder: Decoder[Map[Connector, ProxyServer]] = Decoder.decodeJson.emap(json =>
    Either.catchNonFatal(json.asArray.get.map { value: io.circe.Json =>
        val obj = value.asObject.get
        (obj("connector").get.as[Connector].right.get,
          obj("proxy").get.as[ProxyServer].right.get)
      } toMap).leftMap(t => s"Config.proxyUsage deserialize from json failed. $t")
  )
}
