package com.gtan.repox.admin

import com.gtan.repox.Repo
import com.gtan.repox.config.Config
import com.ning.http.client.{ProxyServer => JProxyServer}

/**
 * Created by xf on 14/12/4.
 */
case class RepoVO(repo: Repo, proxy: Option[JProxyServer]) {
}

object RepoVO {
  def apply(repo: Repo): RepoVO = RepoVO(repo, Config.proxyUsage.get(repo))
}
