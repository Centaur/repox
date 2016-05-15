package com.gtan.repox.admin

import com.gtan.repox.config.Config
import com.gtan.repox.data.{Connector, ProxyServer}

case class ConnectorVO(connector: Connector, proxy: Option[ProxyServer]) {
}

object ConnectorVO {
  def wrap(connector: Connector) = ConnectorVO(connector, Config.proxyUsage.get(connector))
}
