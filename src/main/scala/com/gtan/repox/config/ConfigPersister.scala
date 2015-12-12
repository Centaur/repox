package com.gtan.repox.config

import java.nio.file.Paths

import akka.actor.{Status, ActorLogging, ActorRef}
import akka.persistence._
import com.gtan.repox.config.ConfigPersister.SaveSnapshot
import com.gtan.repox.{SerializationSupport, Repox, RequestQueueMaster}
import com.ning.http.client.{AsyncHttpClient, ProxyServer => JProxyServer}
import io.undertow.Handlers
import io.undertow.server.handlers.resource.{FileResourceManager, ResourceManager}
import io.undertow.util.StatusCodes
import play.api.libs.json.{Json, JsValue}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait Jsonable

trait ConfigCmd extends Jsonable {
  def transform(old: Config): Config = old
}

case class ImportConfig(uploaded: Config) extends ConfigCmd {
  override def transform(old: Config): Config = uploaded.copy(password = old.password)
}

// Everything can be command or Jsonable, but only Evt will be persisted.
trait Evt

case class ConfigChanged(config: Config, configCmd: Jsonable) extends Evt

case object UseDefault extends Evt

object ConfigPersister extends SerializationSupport {

  case object SaveSnapshot

  val ConfigClass = classOf[Config].getName

  override val reader: (JsValue) => PartialFunction[String, Jsonable] = payload => {
    case ConfigClass => payload.as[Config]
  }

  override val writer: PartialFunction[Jsonable, JsValue] = {
    case o: Config => Json.toJson(o)
  }
}

class ConfigPersister extends PersistentActor with ActorLogging {

  import com.gtan.repox.config.ConnectorPersister._
  import com.gtan.repox.config.ParameterPersister._


  override def persistenceId = "Config"

  var config: Config = _
  var saveSnapshotRequester: Option[ActorRef] = None

  def onConfigSaved(sender: ActorRef, c: ConfigChanged) = {
    log.debug(s"event caused by cmd: ${c.configCmd}")
    config = c.config
    for {
      _ <- Config.set(config)
      _ <- c.configCmd match {
        case NewConnector(vo) =>
          Repox.clients.alter { clients =>
            clients.updated(vo.connector.name, vo.connector.createClient)
          }
        case DeleteConnector(id) =>
          Repox.clients.alter { clients =>
            Config.connectors.find(_.id.contains(id)).fold(clients) { connector =>
              for (client <- clients.get(connector.name)) {
                client.closeAsynchronously()
              }
              clients - connector.name
            }
          }
        case UpdateConnector(vo) =>
          Repox.clients.alter { clients =>
            for (client <- clients.get(vo.connector.name)) {
              client.closeAsynchronously()
            }
            clients.updated(vo.connector.name, vo.connector.createClient)
          }
        case SetExtraResources(_) =>
          Repox.resourceHandlers.alter((for (er <- Config.resourceBases) yield {
            val resourceManager: FileResourceManager = new FileResourceManager(Paths.get(er).toFile, 100 * 1024)
            val resourceHandler = Handlers.resource(resourceManager)
            resourceManager -> resourceHandler
          }).toMap)
          Future {
            Map.empty[String, AsyncHttpClient]
          }
        case ImportConfig(_) =>
          for(client <- Repox.clients.get.valuesIterator) {
            client.closeAsynchronously()
          }
          for(manager <- Repox.resourceHandlers.get.keysIterator) {
            manager.close()
          }
          val fut1 = Repox.initClients()
          val fut2 = Repox.initResourceManagers()
          for (both <- fut1 zip fut2) yield {
            Map.empty[String, AsyncHttpClient]
          }
        case _ => Future {
          Map.empty[String, AsyncHttpClient]
        }
      }
    } {
      sender ! StatusCodes.OK
    }
  }

  val receiveCommand: Receive = {
    case cmd: ConfigCmd =>
      val newConfig = cmd.transform(config)
      if (newConfig == config) {
        // no change
        sender ! StatusCodes.OK
      } else {
        persist(ConfigChanged(newConfig, cmd))(onConfigSaved(sender(), _))
      }
    case UseDefault =>
      persist(UseDefault) { _ =>
        config = Config.default
        Config.set(config).foreach { _ =>
          Repox.requestQueueMaster ! RequestQueueMaster.ConfigLoaded
        }
      }
    case SaveSnapshot =>
      saveSnapshot(config)
      saveSnapshotRequester = Some(sender())
    case SaveSnapshotSuccess(metadata) =>
      log.debug(s"Config snapshot saved. Delete old ones.")
      deleteSnapshots(SnapshotSelectionCriteria(maxSequenceNr = metadata.sequenceNr - 1))
    case f@SaveSnapshotFailure(metadata, cause) =>
      log.debug(f.toString)
      for (requester <- saveSnapshotRequester) {
        requester ! Status.Failure(cause)
        saveSnapshotRequester = None
      }
    case DeleteSnapshotsSuccess(criteria) =>
      for (requester <- saveSnapshotRequester) {
        requester ! criteria.maxTimestamp
        saveSnapshotRequester = None
      }
    case DeleteSnapshotsFailure(criteria, cause) =>
      for (requester <- saveSnapshotRequester) {
        requester ! Status.Failure(cause)
        saveSnapshotRequester = None
      }
  }

  val receiveRecover: Receive = {
    case ConfigChanged(data, cmd) =>
      log.debug(s"Config changed, cmd=$cmd")
      config = data

    case UseDefault =>
      config = Config.default

    case SnapshotOffer(metadata, offeredSnapshot) =>
      config = offeredSnapshot.asInstanceOf[Config]

    case RecoveryCompleted =>
      if (config == null) {
        // no config history, save default data as snapshot
        self ! UseDefault
      } else {
        Config.set(config).foreach { _ =>
          Repox.requestQueueMaster ! RequestQueueMaster.ConfigLoaded
        }
      }
  }
}
