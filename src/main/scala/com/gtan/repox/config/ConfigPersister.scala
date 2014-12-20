package com.gtan.repox.config

import akka.actor.{ActorLogging, ActorRef}
import akka.pattern.pipe
import akka.persistence.{PersistentActor, RecoveryCompleted}
import com.gtan.repox.{Repox, RequestQueueMaster}
import com.ning.http.client.{ProxyServer => JProxyServer, AsyncHttpClient}
import io.undertow.util.StatusCodes
import play.api.libs.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait Cmd {
  def transform(old: Config): Config
  def toJson: JsValue
  def serializeToJson = JsObject(
    Seq(
      "manifest" -> JsString(this.getClass.getName),
      "payload" -> toJson
    )
  )

}

trait Evt

case class ConfigChanged(config: Config, cmd: Cmd) extends Evt

case object UseDefault extends Evt

case object ConfigPersister extends RepoPersister with ParameterPersister
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
    Config.set(config)
    val future = c.cmd match {
      case NewConnector(vo) =>
        Repox.clients.alter { clients =>
          clients.updated(vo.connector.name, vo.connector.createClient)
        }
      case DeleteConnector(id) =>
        Repox.clients.alter { clients =>
          Config.connectors.find(_.id == Some(id)).fold(clients) { connector =>
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
      case _ => Future {Map.empty[String, AsyncHttpClient]}
    }
    future.map(_ => StatusCodes.OK) pipeTo sender
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
  }
}
