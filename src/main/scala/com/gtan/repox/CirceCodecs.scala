package com.gtan.repox
import java.time.Instant

import com.gtan.repox.data.{Connector, ProxyServer, Repo}
import com.ning.http.client.ProxyServer.Protocol
import io.circe.Decoder.Result
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import com.ning.http.client.{ProxyServer => JProxyServer}

import scala.concurrent.duration.Duration


case class ConnectorUsage(repo: Repo, connector: Connector)

case class ProxyUsage(connector: Connector, proxy: ProxyServer)

object CirceCodecs {
  implicit val DateTimeEncoder: Encoder[Instant] = new Encoder[Instant] {
    override def apply(a: Instant): Json = a.toEpochMilli.asJson
  }

  implicit val DateTimeDecoder: Decoder[Instant] = new Decoder[Instant] {
    override def apply(c: HCursor): Result[Instant] = c.as[Long].map(Instant.ofEpochMilli)
  }

  implicit val durationDecoder = new Decoder[Duration] {
    override def apply(c: HCursor): Result[Duration] = c.as[String].map(Duration.apply)
  }

  implicit val durationEncoder = new Encoder[Duration] {
    override def apply(a: Duration): Json = a.toString.asJson
  }

  implicit val protocolDecoder = new Decoder[JProxyServer.Protocol] {
    override def apply(c: HCursor): Result[Protocol] = c.as[String].map(JProxyServer.Protocol.valueOf)
  }

  implicit  val protocolEncoder = new Encoder[JProxyServer.Protocol] {
    override def apply(a: Protocol): Json = a.name.asJson
  }

  implicit val connectorUsageDecoder = new Decoder[Map[Repo, Connector]] {
    override def apply(c: HCursor): Result[Map[Repo, Connector]] =
      for (seq <- c.as[Seq[ConnectorUsage]]) yield {
        seq.map(cu => cu.repo -> cu.connector).toMap
      }

  }

  implicit val connectorUsageEncoder = new Encoder[Map[Repo, Connector]] {
    override def apply(a: Map[Repo, Connector]): Json = a map {
      case (repo, connector) => Json.obj("repo" -> repo.asJson, "connector" -> connector.asJson)
    } asJson
  }



  implicit val proxyUsageDecoder = new Decoder[Map[Connector, ProxyServer]] {
    override def apply(c: HCursor): Result[Map[Connector, ProxyServer]] =
      for (seq <- c.as[Seq[ProxyUsage]]) yield {
        seq.map(pu => pu.connector -> pu.proxy).toMap
      }
  }

  implicit val proxyUsageEncoder = new Encoder[Map[Connector, ProxyServer]] {
    override def apply(a: Map[Connector, ProxyServer]): Json = a map {
      case (connector, proxy) => Json.obj("connector" -> connector.asJson, "proxy" -> proxy.asJson)
    } asJson
  }
}
