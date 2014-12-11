package com.gtan.repox.admin

import com.gtan.repox.Repox
import com.gtan.repox.config.Config
import com.gtan.repox.data.{DurationFormat, Connector, ProxyServer, Repo}
import com.ning.http.client.{ProxyServer => JProxyServer}
import play.api.libs.json.Json

import collection.JavaConverters._

case class RepoVO(repo: Repo, connector: Option[Connector])

object RepoVO {
  def wrap(repo: Repo): RepoVO = RepoVO(repo, Config.connectorUsage.get(repo))

  implicit val format = Json.format[RepoVO]
}
