package com.gtan.repox.config

import akka.actor.ActorLogging
import akka.persistence.{PersistentActor, RecoveryCompleted}
import com.gtan.repox.{Immediate404Rule, Repo, Repox, RequestQueueMaster}
import com.ning.http.client.{ProxyServer => JProxyServer}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

object ConfigPersister {

  trait Cmd {
    def transform(old: Config): Config
  }

  case class NewRepo(repo: Repo) extends Cmd {
    override def transform(old: Config) = {
      val oldRepos = old.repos
      old.copy(repos = oldRepos :+ repo)
    }
  }

  case class DeleteRepo(repo: Repo) extends Cmd {
    override def transform(old: Config) = {
      val oldRepos = old.repos
      old.copy(repos = oldRepos.filterNot(_ == repo))
    }
  }

  case class NewOrUpdateProxy(name: String, proxy: JProxyServer) extends Cmd {
    override def transform(old: Config) = {
      val oldProxies = old.proxies
      old.copy(proxies = oldProxies.updated(name, proxy))
    }
  }

  case class DeleteProxy(name: String) extends Cmd {
    override def transform(old: Config) = {
      val oldProxies = old.proxies
      val oldProxyUsage = old.proxyUsage
      old.copy(
        proxies = oldProxies - name,
        proxyUsage = oldProxyUsage.filterNot { case (repo, proxy) => repo.name == name}
      )
    }
  }

  case class RepoUseProxy(repo: Repo, proxy: Option[JProxyServer]) extends Cmd {
    override def transform(old: Config) = {
      val oldProxyUsage = old.proxyUsage
      old.copy(proxyUsage = proxy match {
        case Some(p) => oldProxyUsage.updated(repo, p)
        case None =>
          oldProxyUsage - repo
      })
    }
  }

  case class NewImmediate404Rule(rule: Immediate404Rule) extends Cmd {
    override def transform(old: Config) = {
      val oldRules = old.immediate404Rules
      old.copy(immediate404Rules = oldRules :+ rule)
    }
  }

  case class SetConnectionTimeout(d: Duration) extends Cmd {
    override def transform(old: Config) = {
      old.copy(connectionTimeout = d)
    }
  }

  case class SetConnectionIdleTimeout(d: Duration) extends Cmd {
    override def transform(old: Config) = {
      old.copy(connectionIdleTimeout = d)
    }
  }
  case class SetMainClientMaxConnections(m: Int) extends Cmd {
    override def transform(old: Config) = {
      old.copy(mainClientMaxConnections = m)
    }
  }
  case class SetMainClientMaxConnectionsPerHost(m: Int) extends Cmd {
    override def transform(old: Config) = {
      old.copy(mainClientMaxConnectionsPerHost = m)
    }
  }
  case class SetProxyClientMaxConnections(m: Int) extends Cmd {
    override def transform(old: Config) = {
      old.copy(proxyClientMaxConnections = m)
    }
  }
  case class SetProxyClientMaxConnectionsPerHost(m: Int) extends Cmd {
    override def transform(old: Config) = {
      old.copy(proxyClientMaxConnectionsPerHost = m)
    }
  }

  trait Evt

  case class ConfigChanged(config: Config, cmd: Cmd) extends Evt

  case object UseDefault extends Evt

}

class ConfigPersister extends PersistentActor with ActorLogging {

  import ConfigPersister._
  override def persistenceId = "Config"

  var config: Config = _

  def onConfigSaved(c: ConfigChanged) = {
    log.debug(s"event: $c")
    config = c.config
    Config.set(config)
  }

  val receiveCommand: Receive = {
    case cmd: Cmd =>
      persist(ConfigChanged(cmd.transform(config), cmd))(onConfigSaved)
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
