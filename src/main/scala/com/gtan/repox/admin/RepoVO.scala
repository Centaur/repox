package com.gtan.repox.admin

import com.gtan.repox.Repox
import com.gtan.repox.config.Config
import com.gtan.repox.data.{ProxyServer, Repo}
import com.ning.http.client.{ProxyServer => JProxyServer}
import play.api.libs.json.Json

import collection.JavaConverters._

case class RepoVO(repo: Repo, proxy: Option[ProxyServer])

object RepoVO {
  def wrap(repo: Repo): RepoVO = RepoVO(repo, Config.proxyUsage.get(repo))

  implicit val format = Json.format[RepoVO]
}
