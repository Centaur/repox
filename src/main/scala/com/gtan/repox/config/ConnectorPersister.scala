package com.gtan.repox.config

import com.gtan.repox.admin.{ConnectorVO, RepoVO}
import com.gtan.repox.data.{Connector, Repo}

trait ConnectorPersister {

  case class NewConnector(vo: ConnectorVO) extends Cmd {
    override def transform(old: Config) = {
      val oldConnectors = old.connectors
      val oldProxyUsage = old.proxyUsage
      // ToDo: validation
      val voWithId = vo.copy(connector = vo.connector.copy(id = Some(Connector.nextId)))
      val newConfig = old.copy(connectors = oldConnectors + voWithId.connector)
      vo.proxy match {
        case None => newConfig
        case Some(p) => newConfig.copy(proxyUsage = oldProxyUsage.updated(voWithId.connector, p))
      }
    }
  }


  case class UpdateConnector(vo: ConnectorVO) extends Cmd {
    override def transform(old: Config) = {
      val oldConnectors = old.connectors
      val oldProxyUsage = old.proxyUsage
      val id = vo.connector.id
      val newConfig = old.copy(connectors = oldConnectors.map{
        case Connector(`id`, _, _, _, _, _) => vo.connector
        case c => c
      })
      vo.proxy.fold(newConfig){p =>
        newConfig.copy(proxyUsage = oldProxyUsage.updated(vo.connector, p))
      }
    }
  }

  case class DeleteConnector(id: Long) extends Cmd {
    override def transform(old: Config) = {
      val oldConnectors = old.connectors
      val oldConnectorUsage = old.connectorUsage
      old.copy(
        connectors = oldConnectors.filterNot(_.id == Some(id)),
        connectorUsage = oldConnectorUsage.filterNot { case (repo, connector) => repo.id == Some(id)}
      )
    }
  }

}