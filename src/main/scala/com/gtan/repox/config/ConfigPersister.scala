package com.gtan.repox.config

import java.nio.file.Paths

import akka.actor.{ActorLogging, ActorRef}
import akka.pattern.pipe
import akka.persistence.{RecoveryFailure, PersistentActor, RecoveryCompleted}
import com.gtan.repox.{Repox, RequestQueueMaster}
import com.ning.http.client.{ProxyServer => JProxyServer, AsyncHttpClient}
import io.undertow.Handlers
import io.undertow.server.handlers.resource.{ResourceManager, FileResourceManager}
import io.undertow.util.StatusCodes
import play.api.libs.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait Cmd {
  def transform(old: Config): Config
}

trait Evt

case class ConfigChanged(config: Config, cmd: Cmd) extends Evt

case object UseDefault extends Evt

object ConfigPersister extends RepoPersister with ParameterPersister
                               with ConnectorPersister
                               with ProxyPersister
                               with Immediate404RulePersister
                               with ExpireRulePersister

class ConfigPersister extends PersistentActor with ActorLogging {

  import com.gtan.repox.config.ConfigPersister._

  override def persistenceId = "Config"

  var config: Config = _

  def onConfigSaved(sender: ActorRef, c: ConfigChanged) = {
    log.debug(s"event caused by cmd: ${c.cmd}")
    config = c.config
    for {
      _ <- Config.set(config)
      _ <- c.cmd match {
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
            val resourceManager: ResourceManager = new FileResourceManager(Paths.get(er).toFile, 100 * 1024)
            val resourceHandler = Handlers.resource(resourceManager)
            resourceManager -> resourceHandler
          }).toMap)
          Future {
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
    case cmd: Cmd =>
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
  }

  val receiveRecover: Receive = {
    case ConfigChanged(data, cmd) =>
      log.debug(s"data=$data, cmd=$cmd")
      config = data

    case UseDefault =>
      config = Config.default

    case RecoveryCompleted =>
      if (config == null) {
        // no config history, save default data as snapshot
        self ! UseDefault
      } else {
        Config.set(config).foreach { _ =>
          Repox.requestQueueMaster ! RequestQueueMaster.ConfigLoaded
        }
      }

    case RecoveryFailure(t) =>
      t.printStackTrace()
  }
}
