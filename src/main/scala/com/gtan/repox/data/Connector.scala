package com.gtan.repox.data

import java.util.concurrent.atomic.AtomicLong

import com.gtan.repox.config.Config
import com.ning.http.client.{AsyncHttpClientConfig, AsyncHttpClient}
import play.api.libs.json.Json

import scala.concurrent.duration.Duration

case class Connector(id: Option[Long],
                     name: String,
                     connectionTimeout: Duration,
                     connectionIdleTimeout: Duration,
                     maxConnections: Int,
                     maxConnectionsPerHost: Int) {


  def createClient = {
    val configBuilder = new AsyncHttpClientConfig.Builder()
      .setRequestTimeoutInMs(Int.MaxValue)
      .setConnectionTimeoutInMs(connectionTimeout.toMillis.toInt)
      .setIdleConnectionInPoolTimeoutInMs(connectionIdleTimeout.toMillis.toInt)
      .setIdleConnectionTimeoutInMs(connectionIdleTimeout.toMillis.toInt)
      .setAllowPoolingConnection(true)
      .setAllowSslConnectionPool(true)
      .setMaximumConnectionsPerHost(maxConnections)
      .setMaximumConnectionsTotal(maxConnectionsPerHost)
      .setFollowRedirects(true)
    val builder = Config.proxyUsage.get(this).fold(configBuilder){x => configBuilder.setProxyServer(x.toJava)}
    new AsyncHttpClient(builder.build())
  }
}

object Connector {
  lazy val nextId: AtomicLong = new AtomicLong(Config.connectors.flatMap(_.id).max)

  import DurationFormat._

  implicit val format = Json.format[Connector]
}
