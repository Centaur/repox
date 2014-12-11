package com.gtan.repox.admin

import com.gtan.repox.config.Config
import com.gtan.repox.data.{Connector, ProxyServer}
import play.api.libs.json.Json

case class ConnectorVO(connector: Connector, proxy: Option[ProxyServer]) {
}

object ConnectorVO {
  def wrap(connector: Connector) = ConnectorVO(connector, Config.proxyUsage.get(connector))

  implicit val format = Json.format[ConnectorVO]
}
