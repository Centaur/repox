package com.gtan.repox.config

import com.gtan.repox.SerializationSupport
import com.gtan.repox.admin.{ConnectorVO, RepoVO}
import com.gtan.repox.data.{Connector, Repo}
import play.api.libs.json.{JsValue, Json}

trait ConnectorPersister {

  case class NewConnector(vo: ConnectorVO) extends Cmd {
    override def transform(old: Config) = {
      val oldConnectors = old.connectors
      val oldProxyUsage = old.proxyUsage
      // ToDo: validation
      val voWithId = vo.copy(connector = vo.connector.copy(id = Some(Connector.nextId.incrementAndGet())))
      val newConfig = old.copy(connectors = oldConnectors + voWithId.connector)
      vo.proxy match {
        case None => newConfig
        case Some(p) => newConfig.copy(proxyUsage = oldProxyUsage.updated(voWithId.connector, p))
      }
    }
  }

  object NewConnector {
    implicit val format = Json.format[NewConnector]
  }


  case class UpdateConnector(vo: ConnectorVO) extends Cmd {
    override def transform(old: Config) = {
      val oldConnectors = old.connectors
      val oldProxyUsage = old.proxyUsage
      val id = vo.connector.id
      val newConfig = old.copy(connectors = oldConnectors.map{
        case Connector(`id`, _, _, _, _, _, _) => vo.connector
        case c => c
      })
      vo.proxy.fold(newConfig){p =>
        newConfig.copy(proxyUsage = oldProxyUsage.updated(vo.connector, p))
      }
    }
  }

  object UpdateConnector {
    implicit val format = Json.format[UpdateConnector]
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

  object DeleteConnector {
    implicit val format = Json.format[DeleteConnector]
  }
}

object ConnectorPersister extends SerializationSupport {
  import ConfigPersister._

  val NewConnectorClass                = classOf[NewConnector].getName
  val UpdateConnectorClass             = classOf[UpdateConnector].getName
  val DeleteConnectorClass             = classOf[DeleteConnector].getName

  override val reader: (JsValue) => PartialFunction[String, Cmd] = payload => {
    case NewConnectorClass => payload.as[NewConnector]
    case UpdateConnectorClass => payload.as[UpdateConnector]
    case DeleteConnectorClass => payload.as[DeleteConnector]
  }
  override val writer  : PartialFunction[Cmd, JsValue]             = {
    case o: NewConnector => Json.toJson(o)
    case o: UpdateConnector => Json.toJson(o)
    case o: DeleteConnector => Json.toJson(o)
  }
}