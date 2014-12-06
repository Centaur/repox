package com.gtan.repox.config

import com.gtan.repox.data.{ProxyServer, Repo}

trait ProxyPersister {
  case class NewOrUpdateProxy(id: Long, proxy: ProxyServer) extends Cmd {
    override def transform(old: Config) = {
      val oldProxies = old.proxies
      old.copy(proxies = oldProxies.map {
        case ProxyServer(proxy.id, _, _, _, _) => proxy
        case p => p
      })
    }
  }

  case class DeleteProxy(id: Long) extends Cmd {
    override def transform(old: Config) = {
      val oldProxies = old.proxies
      val oldProxyUsage = old.proxyUsage
      old.copy(
        proxies = oldProxies.filterNot(_.id == id),
        proxyUsage = oldProxyUsage.filterNot { case (repo, proxy) => proxy.id == id}
      )
    }
  }

  case class RepoUseProxy(repo: Repo, proxy: Option[ProxyServer]) extends Cmd {
    override def transform(old: Config) = {
      val oldProxyUsage = old.proxyUsage
      old.copy(proxyUsage = proxy match {
        case Some(p) => oldProxyUsage.updated(repo, p)
        case None =>
          oldProxyUsage - repo
      })
    }
  }

}
