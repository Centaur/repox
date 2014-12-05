package com.gtan.repox.admin

import com.gtan.repox.Repo
import com.gtan.repox.config.Config
import com.ning.http.client.{ProxyServer => JProxyServer}

import collection.JavaConverters._

/**
 * Created by xf on 14/12/4.
 */
case class RepoVO(repo: Repo, proxy: Option[ProxyServer]) {
  def toMap: java.util.Map[String, Any] = {
    val withoutProxy = repo.toMap
    val withProxy = proxy.fold(withoutProxy)(p => withoutProxy.updated("proxy", p))
    withProxy.asJava
  }
}

object RepoVO {
  def apply(repo: Repo): RepoVO = RepoVO(repo, Config.proxyUsage.get(repo))
}
