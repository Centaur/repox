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
                     maxConnectionsPerHost: Int) {


  def createClient = {
    val configBuilder = new AsyncHttpClientConfig.Builder()
      .setRequestTimeout(Int.MaxValue)
      .setConnectTimeout(connectionTimeout.toMillis.toInt)
      .setAllowPoolingConnections(true)
      .setAllowPoolingSslConnections(true)
      .setMaxConnectionsPerHost(maxConnections)
      .setMaxConnections(maxConnectionsPerHost)
      .setPooledConnectionIdleTimeout(connectionIdleTimeout.toMillis.toInt)
      .setFollowRedirect(true)
    val builder = Config.proxyUsage.get(this).fold(configBuilder){x => configBuilder.setProxyServer(x.toJava)}
    new AsyncHttpClient(builder.build())
  }
}

object Connector {
  def nextId:Long = Config.connectors.flatMap(_.id).max + 1

  import DurationFormat._

  implicit val format = Json.format[Connector]
}
