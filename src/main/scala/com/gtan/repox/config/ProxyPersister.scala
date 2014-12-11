package com.gtan.repox.config

import com.gtan.repox.data.{ProxyServer, Repo}

trait ProxyPersister {

  case class NewOrUpdateProxy(proxy: ProxyServer) extends Cmd {
    override def transform(old: Config) = {
      val oldProxies = old.proxies
      old.copy(proxies = proxy.id.fold(oldProxies :+ proxy.copy(id = Some(ProxyServer.nextId))) { _id => oldProxies.map {
        case ProxyServer(Some(`_id`), _, _, _, _, _) => proxy
        case p => p
      }
      })
    }
  }

  case class EnableProxy(id: Long) extends Cmd {
    override def transform(old: Config) = {
      val oldProxies = old.proxies
      old.copy(proxies = oldProxies.map {
        case p@ProxyServer(Some(`id`), _, _, _, _, _) => p.copy(disabled = false)
        case p => p
      })
    }
  }

  case class DisableProxy(id: Long) extends Cmd {
    override def transform(old: Config) = {
      val oldProxies = old.proxies
      old.copy(proxies = oldProxies.map {
        case p@ProxyServer(Some(`id`), _, _, _, _, _) => p.copy(disabled = true)
        case p => p
      })
    }
  }

  case class DeleteProxy(id: Long) extends Cmd {
    override def transform(old: Config) = {
      val oldProxies = old.proxies
      val oldProxyUsage = old.connectorUsage
      old.copy(
        proxies = oldProxies.filterNot(_.id == Some(id)),
        connectorUsage = oldProxyUsage.filterNot { case (repo, proxy) => proxy.id == Some(id)}
      )
    }
  }

}
