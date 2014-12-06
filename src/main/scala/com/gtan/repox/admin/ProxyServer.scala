package com.gtan.repox.admin

import com.gtan.repox.Repox
import com.ning.http.client.{ProxyServer => JProxyServer}
import collection.JavaConverters._

/**
 * Created by xf on 14/12/4.
 */
case class ProxyServer(id: Long, name: String, protocol: JProxyServer.Protocol, host: String, port: Int) {
  def toJava: JProxyServer = new JProxyServer(protocol, host, port)

  def toMap: java.util.Map[String, Any] = {
    val withoutId = Map(
      "type" -> protocol,
      "name" -> name,
      "host" -> host,
      "port" -> port
    )
    val withId =
      if (id == -1) withoutId
      else withoutId.updated("id", id)
    withId.asJava
  }
}

object ProxyServer {
  def fromJson(json: String): ProxyServer = {
    val map = Repox.gson.fromJson(json, classOf[java.util.Map[String, String]])
    val withoutId = ProxyServer(
      id = -1,
      name = map.get("name"),
      protocol = JProxyServer.Protocol.valueOf(map.get("protocol")),
      map.get("host"),
      map.get("port").toInt
    )
    if(map.containsKey("id"))
      withoutId.copy(id = map.get("id").toLong)
    else withoutId
  }

}
