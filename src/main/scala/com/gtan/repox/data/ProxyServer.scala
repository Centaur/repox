package com.gtan.repox.data

import com.gtan.repox.Repox
import com.gtan.repox.config.Config
import com.ning.http.client.ProxyServer.Protocol
import com.ning.http.client.{ProxyServer => JProxyServer}
import play.api.libs.json._

import scala.collection.JavaConverters._

/**
 * Created by xf on 14/12/4.
 */
case class ProxyServer(id: Option[Long], name: String, protocol: JProxyServer.Protocol, host: String, port: Int, disabled: Boolean = false) {
  def toJava: JProxyServer = new JProxyServer(protocol, host, port)

}

object ProxyServer {
  // FixMe: need a threadsafe nextId
  def nextId: Long = Config.proxies.flatMap(_.id).max + 1

  implicit val protocolFormat = new Format[JProxyServer.Protocol] {
    override def reads(json: JsValue):JsResult[JProxyServer.Protocol] = json match {
      case JsString(str) =>
        JsSuccess(JProxyServer.Protocol.valueOf(str))
      case _ =>
        JsError("not a valid protocol")
    }

    override def writes(o: Protocol) = JsString(o.name())
  }

  implicit val format = Json.format[ProxyServer]
}
