package com.gtan.repox.data

import com.gtan.repox.Repox
import com.gtan.repox.config.Config
import com.ning.http.client.{ProxyServer => JProxyServer}

import scala.collection.JavaConverters._

/**
 * Created by xf on 14/12/4.
 */
case class ProxyServer(id: Option[Long], name: String, protocol: JProxyServer.Protocol, host: String, port: Int, disabled: Boolean = false) {
  def toJava: JProxyServer = new JProxyServer(protocol, host, port)

  def toMap: java.util.Map[String, Any] = {
    val withoutId = Map(
      "protocol" -> protocol,
      "name" -> name,
      "host" -> host,
      "port" -> port,
      "disabled" -> disabled
    )
    val withId = id.fold(withoutId) { _id =>
        withoutId.updated("id", _id)
      }
    withId.asJava
  }
}

object ProxyServer {
  // FixMe: need a threadsafe nextId
  def nextId: Long = Config.proxies.flatMap(_.id).max + 1

  def fromJson(json: String): ProxyServer = {
    val map = Repox.gson.fromJson(json, classOf[java.util.Map[String, String]]).asScala
    ProxyServer(
      id = map.get("id").map(_.toLong),
      name = map("name"),
      protocol = JProxyServer.Protocol.valueOf(map("protocol")),
      map("host"),
      map("port").toInt,
      map("disabled").toBoolean
    )
  }

}
