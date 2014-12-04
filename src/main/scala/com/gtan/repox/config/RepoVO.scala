package com.gtan.repox.config

import com.gtan.repox.Repo
import com.ning.http.client.{ProxyServer => JProxyServer}

/**
 * Created by xf on 14/12/4.
 */
case class RepoVO(repo: Repo, proxy: Option[JProxyServer]) {
}

object RepoVO {
  def apply(repo: Repo): RepoVO = RepoVO(repo, Config.proxyUsage.get(repo))
}
