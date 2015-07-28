package com.gtan.repox.config

import com.gtan.repox.data.{ProxyServer, Connector, Repo}
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

    override def reads(json: JsValue) = json match {
      case JsArray(values) =>
        JsSuccess(values.map {
          case JsObject(Seq(
          ("repo", repoJsVal: JsValue),
          ("connector", connectorJsVal: JsValue))) =>
            repoJsVal.as[Repo] -> connectorJsVal.as[Connector]
        }.toMap)
      case _ => JsError("Config.connectorUsage deserialize from json failed.")
    }
  }

  implicit val proxyUsageFormat = new Format[Map[Connector, ProxyServer]]{
    override def writes(o: Map[Connector, ProxyServer]) = JsArray(o.map {
          case (connector, proxy) => JsObject(
            Seq(
              "connector" -> Json.toJson(connector),
              "proxy" -> Json.toJson(proxy)
            )
          )
        }.toSeq)

    override def reads(json: JsValue) = json match {
          case JsArray(values) =>
            JsSuccess(values.map {
              case JsObject(Seq(
              ("connector", connectorJsVal:JsValue),
              ("proxy", proxyJsVal:JsValue))) =>
                connectorJsVal.as[Connector] -> proxyJsVal.as[ProxyServer]
            }.toMap)
          case _ => JsError("Config.proxyUsage deserialize from json failed.")
        }
  }
  import com.gtan.repox.data.DurationFormat._
  implicit val format = Json.format[Config]

}
