package com.gtan.repox.config

import com.gtan.repox.data.{Connector, ProxyServer, Repo}
import play.api.libs.json._

trait ConfigFormats {
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

  import com.gtan.repox.data.DurationFormat._

  implicit val format = Json.format[Config]

}
