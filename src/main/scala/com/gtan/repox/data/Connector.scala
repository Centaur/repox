package com.gtan.repox.data

import com.gtan.repox.config.Config
import com.ning.http.client.{AsyncHttpClientConfig, AsyncHttpClient}
import play.api.libs.json.Json

import scala.concurrent.duration.Duration

case class Connector(id: Option[Long],
                     name: String,
                     connectionTimeout: Duration,
                     connectionIdleTimeout: Duration,
                     maxConnections: Int,
                     maxConnectionsPerHost: Int,
                     proxy: Option[ProxyServer] = None) {
  val configBuilder = new AsyncHttpClientConfig.Builder()
    .setRequestTimeoutInMs(Int.MaxValue)
    .setConnectionTimeoutInMs(connectionTimeout.toMillis.toInt)
    .setAllowPoolingConnection(true)
    .setAllowSslConnectionPool(true)
    .setMaximumConnectionsPerHost(maxConnections)
    .setMaximumConnectionsTotal(maxConnectionsPerHost)
    .setIdleConnectionInPoolTimeoutInMs(connectionIdleTimeout.toMillis.toInt)
    .setIdleConnectionTimeoutInMs(connectionIdleTimeout.toMillis.toInt)
    .setFollowRedirects(true)

  proxy.fold(configBuilder) { p =>
    configBuilder.setProxyServer(p.toJava)
  }

  def createClient = new AsyncHttpClient(configBuilder.build())
}

object Connector {
  import DurationFormat._

  implicit val format = Json.format[Connector]
}
