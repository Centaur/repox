package com.gtan.repox.data

import java.util.concurrent.atomic.AtomicLong

import com.gtan.repox.config.Config
import com.ning.http.client.ProxyServer.Protocol
import com.ning.http.client.{ProxyServer => JProxyServer}


case class ProxyServer(id: Option[Long], name: String, protocol: JProxyServer.Protocol, host: String, port: Int, disabled: Boolean = false) {
  def toJava: JProxyServer = new JProxyServer(protocol, host, port)

}

object ProxyServer {
  lazy val nextId: AtomicLong = new AtomicLong(Config.proxies.flatMap(_.id).reduceOption[Long](math.max).getOrElse(1))
}
