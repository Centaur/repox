package com.gtan.repox.config

import com.gtan.repox.SerializationSupport
import com.gtan.repox.admin.{ConnectorVO, RepoVO}
import com.gtan.repox.data.{Connector, Repo}
import play.api.libs.json.{JsValue, Json}

object ConnectorPersister extends SerializationSupport {

  case class NewConnector(vo: ConnectorVO) extends ConfigCmd {
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

  implicit val NewConnectorFormat = Json.format[NewConnector]


  case class UpdateConnector(vo: ConnectorVO) extends ConfigCmd {
    override def transform(old: Config) = {
      val oldConnectors = old.connectors
      val oldProxyUsage = old.proxyUsage
      val id = vo.connector.id
      val newConfig = old.copy(
        connectors = oldConnectors.map {
          case Connector(`id`, _, _, _, _, _, _) => vo.connector
          case c => c
        },
        connectorUsage = old.connectorUsage.map {
          case (repo, Connector(`id`, _, _, _, _, _, _)) => (repo, vo.connector)
          case p => p
        }
      )
      vo.proxy.fold(newConfig) { p =>
        newConfig.copy(proxyUsage = oldProxyUsage.updated(vo.connector, p))
      }
    }
  }

  implicit val updateConnectorFormat = Json.format[UpdateConnector]

  case class DeleteConnector(id: Long) extends ConfigCmd {
    override def transform(old: Config) = {
      val oldConnectors = old.connectors
      val oldConnectorUsage = old.connectorUsage
      old.copy(
        connectors = oldConnectors.filterNot(_.id.contains(id)),
        connectorUsage = oldConnectorUsage.filterNot { case (repo, connector) => repo.id.contains(id) }
      )
    }
  }

  implicit val DeleteConnectorFormat = Json.format[DeleteConnector]

  val NewConnectorClass = classOf[NewConnector].getName
  val UpdateConnectorClass = classOf[UpdateConnector].getName
  val DeleteConnectorClass = classOf[DeleteConnector].getName

  override val reader: (JsValue) => PartialFunction[String, Jsonable] = payload => {
    case NewConnectorClass => payload.as[NewConnector]
    case UpdateConnectorClass => payload.as[UpdateConnector]
    case DeleteConnectorClass => payload.as[DeleteConnector]
  }
  override val writer: PartialFunction[Jsonable, JsValue] = {
    case o: NewConnector => Json.toJson(o)
    case o: UpdateConnector => Json.toJson(o)
    case o: DeleteConnector => Json.toJson(o)
  }
}