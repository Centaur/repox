package com.gtan.repox.config

import com.gtan.repox.SerializationSupport
import com.gtan.repox.admin.{ConnectorVO, RepoVO}
import com.gtan.repox.data.{Connector, Repo}
import io.circe.Decoder.Result
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._


object ConnectorPersister extends SerializationSupport {
  import com.gtan.repox.CirceCodecs.{durationDecoder, durationEncoder, protocolDecoder, protocolEncoder, connectorUsageDecoder, connectorUsageEncoder}
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

  val NewConnectorClass = classOf[NewConnector].getName
  val UpdateConnectorClass = classOf[UpdateConnector].getName
  val DeleteConnectorClass = classOf[DeleteConnector].getName

  override val reader: Json => PartialFunction[String, Result[Jsonable]] = payload => {
    case NewConnectorClass => payload.as[NewConnector]
    case UpdateConnectorClass => payload.as[UpdateConnector]
    case DeleteConnectorClass => payload.as[DeleteConnector]
  }

  import io.circe.syntax._
  override val writer: PartialFunction[Jsonable, Json] = {
    case o: NewConnector => o.asJson
    case o: UpdateConnector => o.asJson
    case o: DeleteConnector => o.asJson
  }
}