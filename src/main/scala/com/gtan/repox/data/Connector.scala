package com.gtan.repox.data

import java.util.concurrent.atomic.AtomicLong

import com.gtan.repox.config.Config
import com.ning.http.client.Realm.{AuthScheme, RealmBuilder}
import com.ning.http.client.{AsyncHttpClient, AsyncHttpClientConfig}
import play.api.libs.json.Json

import scala.concurrent.duration.Duration
import com.ning.http.client.{Realm => JRealm}
import io.circe.generic.JsonCodec
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}

case class Realm(user: String, password: String, scheme: String) {
  def toJava: JRealm = new RealmBuilder().setPrincipal(user)
    .setPassword(password)
    .setUsePreemptiveAuth(true)
    .setScheme(AuthScheme.valueOf(scheme))
    .build()
}

object Realm {
  implicit val realmEncoder = deriveEncoder[Realm]
  implicit val realmDecoder = deriveDecoder[Realm]
}

case class Connector(id: Option[Long],
                     name: String,
                     connectionTimeout: Duration,
                     connectionIdleTimeout: Duration,
                     maxConnections: Int,
                     maxConnectionsPerHost: Int,
                     credentials: Option[Realm] = None) {


  def createClient = {
    val configBuilder = new AsyncHttpClientConfig.Builder()
      .setRequestTimeout(Int.MaxValue)
      .setReadTimeout(connectionIdleTimeout.toMillis.toInt)
      .setConnectTimeout(connectionTimeout.toMillis.toInt)
      .setPooledConnectionIdleTimeout(connectionIdleTimeout.toMillis.toInt)
      .setAllowPoolingConnections(true)
      .setAllowPoolingSslConnections(true)
      .setMaxConnectionsPerHost(maxConnections)
      .setMaxConnections(maxConnectionsPerHost)
      .setFollowRedirect(true)
    val withCredentials = this.credentials.fold(configBuilder) { x => configBuilder.setRealm(x.toJava) }
    val builder = Config.proxyUsage.get(this).fold(withCredentials) { x => withCredentials.setProxyServer(x.toJava) }

    new AsyncHttpClient(builder.build())
  }
}

object Connector extends DurationFormat{
  lazy val nextId: AtomicLong = new AtomicLong(Config.connectors.flatMap(_.id).reduceOption[Long](math.max).getOrElse(1L))

  implicit val realmFormat = Json.format[Realm]
  implicit val format = Json.format[Connector]

  implicit val connectorEncoder = deriveEncoder[Connector]
  implicit val connectorDecoder = deriveDecoder[Connector]
}
