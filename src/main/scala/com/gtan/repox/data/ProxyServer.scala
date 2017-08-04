package com.gtan.repox.data

import java.util.concurrent.atomic.AtomicLong

import com.gtan.repox.Repox
import com.gtan.repox.config.Config
import com.ning.http.client.ProxyServer.Protocol
import com.ning.http.client.{ProxyServer => JProxyServer}
import io.circe.{Decoder, Encoder, ObjectEncoder}
import io.circe.generic.JsonCodec
import play.api.libs.json._

import scala.collection.JavaConverters._
import io.circe.generic.semiauto._

case class ProxyServer(id: Option[Long], name: String, protocol: JProxyServer.Protocol, host: String, port: Int, disabled: Boolean = false) {
  def toJava: JProxyServer = new JProxyServer(protocol, host, port)
}

object ProxyServer {
  lazy val nextId: AtomicLong = new AtomicLong(Config.proxies.flatMap(_.id).reduceOption[Long](math.max).getOrElse(1))

  implicit val protocolFormat = new Format[JProxyServer.Protocol] {
    override def reads(json: JsValue): JsResult[JProxyServer.Protocol] = json match {
      case JsString(str) =>
        JsSuccess(JProxyServer.Protocol.valueOf(str))
      case _ =>
        JsError("not a valid protocol")
    }

    override def writes(o: Protocol) = JsString(o.name())
  }

  implicit val format = Json.format[ProxyServer]

  implicit val encodeProtocol: Encoder[Protocol] = Encoder.encodeString.contramap[JProxyServer.Protocol](_.name())
  implicit val decodeProtocol: Decoder[Protocol] = Decoder.decodeString.emap { str =>
    Right(JProxyServer.Protocol.valueOf(str))
  }

  implicit val proxyServerDecoder: Decoder[ProxyServer] = deriveDecoder[ProxyServer]
  implicit val proxyServerEncoder: Encoder[ProxyServer] = deriveEncoder[ProxyServer]
}
