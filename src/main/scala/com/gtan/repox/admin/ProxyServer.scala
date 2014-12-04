package com.gtan.repox.admin

import com.ning.http.client.{ProxyServer => JProxyServer}

/**
 * Created by xf on 14/12/4.
 */
object ProxyServer {
  def apply(json: String): JProxyServer = {
    val map = Jsonable.gson.fromJson(json, classOf[java.util.Map[String, String]])
    new JProxyServer(
      JProxyServer.Protocol.valueOf(map.get("protocol")),
      map.get("host"),
      map.get("port").toInt)
  }
}
