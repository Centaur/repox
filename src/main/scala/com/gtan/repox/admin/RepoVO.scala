package com.gtan.repox.admin

import com.gtan.repox.Repox
import com.gtan.repox.config.Config
import com.gtan.repox.data.{ProxyServer, Repo}
import com.ning.http.client.{ProxyServer => JProxyServer}

import collection.JavaConverters._

case class RepoVO(repo: Repo, proxy: Option[ProxyServer]) {
  def toMap: java.util.Map[String, Any] = {
    val withoutProxy = repo.toMap
    val withProxy = proxy.fold(withoutProxy)(p => withoutProxy.updated("proxy", p))
    withProxy.asJava
  }
}

object RepoVO {
  def apply(repo: Repo): RepoVO = RepoVO(repo, Config.proxyUsage.get(repo))

  def fromJson(json: String): RepoVO = {
    val map = Repox.gson.fromJson(json, classOf[java.util.Map[String, String]]).asScala
    val repo = Repo.fromJson(json)
    RepoVO(repo, map.get("proxy").map(ProxyServer.fromJson))
  }
}
