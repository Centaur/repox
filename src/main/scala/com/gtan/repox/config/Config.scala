package com.gtan.repox.config

import java.nio.file.{Path, Paths}

import akka.agent.Agent
import com.gtan.repox.data.{ExpireRule, ProxyServer, Repo, Immediate404Rule}
import com.gtan.repox.Repox
import com.ning.http.client.{AsyncHttpClient, ProxyServer => JProxyServer}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * Created by IntelliJ IDEA.
 * User: xf
 * Date: 14/11/30
 * Time: 下午3:10
 */


case class Config(proxies: Seq[ProxyServer],
                  repos: IndexedSeq[Repo],
                  proxyUsage: Map[Repo, ProxyServer],
                  immediate404Rules: Seq[Immediate404Rule],
                  expireRules: Seq[ExpireRule],
                  storage: String,
                  connectionTimeout: Duration,
                  connectionIdleTimeout: Duration,
                  mainClientMaxConnectionsPerHost: Int,
                  mainClientMaxConnections: Int,
                  proxyClientMaxConnectionsPerHost: Int,
                  proxyClientMaxConnections: Int)

object Config extends LazyLogging{
  val defaultProxies = List(
    ProxyServer(id = Some(1), name = "Lantern", protocol = JProxyServer.Protocol.HTTP, host = "localhost", port = 8787)
  )
  val defaultRepos: IndexedSeq[Repo] = IndexedSeq(
    Repo(Some(1), "koala", "http://nexus.openkoala.org/nexus/content/groups/Koala-release",
      priority = 1, getOnly = true, maven = true),
    Repo(Some(2), "sonatype", "http://oss.sonatype.org/content/repositories/releases", priority = 2),
    Repo(Some(3), "typesafe", "http://repo.typesafe.com/typesafe/releases", priority = 2),
    Repo(Some(4), "oschina", "http://maven.oschina.net/content/groups/public",
      priority = 2, getOnly = true, maven = true),
    Repo(Some(5), "sbt-plugin", "http://dl.bintray.com/sbt/sbt-plugin-releases", priority = 4),
    Repo(Some(6), "scalaz", "http://dl.bintray.com/scalaz/releases", priority = 4),
    Repo(Some(7), "central", "http://repo1.maven.org/maven2", priority = 4, maven = true),
    Repo(Some(8), "ibiblio", "http://mirrors.ibiblio.org/maven2", priority = 5, maven = true)
  )

  val defaultImmediate404Rules: Seq[Immediate404Rule] = Vector(
    Immediate404Rule(Some(1), """.+-javadoc\.jar"""), // we don't want javadoc
    Immediate404Rule(Some(2), """.+-parent.*\.jar"""), // parent have no jar
    Immediate404Rule(Some(3), """/org/scala-sbt/.*""", exclude = Some( """/org/scala-sbt/test-interface/.*""")), // ivy only artifact have no maven uri
    //    Immediat404Rule( """/org/scala-tools/.*"""), // ivy only artifact have no maven uri
    Immediate404Rule(Some(4), """/com/eed3si9n/.*"""), // ivy only artifact have no maven uri
    Immediate404Rule(Some(5), """/io\.spray/.*""", exclude = Some( """/io\.spray/sbt-revolver.*""")), // maven only artifact have no ivy uri
    Immediate404Rule(Some(6), """/org/jboss/xnio/xnio-all/.+\.jar"""),
    Immediate404Rule(Some(7), """/org\.jboss\.xnio/xnio-all/.+\.jar"""),
    Immediate404Rule(Some(8), """/org/apache/apache/(\d+)/.+\.jar"""),
    Immediate404Rule(Some(9), """/org\.apache/apache/(\d+)/.+\.jar"""),
    Immediate404Rule(Some(10), """/com/google/google/(\d+)/.+\.jar"""),
    Immediate404Rule(Some(11), """/com\.google/google/(\d+)/.+\.jar"""),
    Immediate404Rule(Some(12), """/org/ow2/ow2/.+\.jar"""),
    Immediate404Rule(Some(13), """/org\.ow2/ow2/.+\.jar"""),
    Immediate404Rule(Some(14), """/com/github/mpeltonen/sbt-idea/.*\.jar"""),
    Immediate404Rule(Some(15), """/com\.github\.mpeltonen/sbt-idea/.*\.jar"""),
    Immediate404Rule(Some(16), """/org/fusesource/leveldbjni/.+-sources\.jar"""),
    Immediate404Rule(Some(17), """/org\.fusesource\.leveldbjni/.+-sources\.jar"""),
    Immediate404Rule(Some(18), """.*/jsr305.*\-sources\.jar""")
  )

  def defaultExpireRules = Seq(
    ExpireRule(Some(1), ".+/maven-metadata.xml", 1 day)
  )

  //  def seq2map[T](s: Seq[T]): Map[Long, T] = s.groupBy(_.id).map({ case (k, v) => k -> v.head})

  val default = Config(
    proxies = defaultProxies,
    repos = defaultRepos,
    proxyUsage = Map(),
    immediate404Rules = defaultImmediate404Rules,
    expireRules = defaultExpireRules,
    storage = Paths.get(System.getProperty("user.home"), ".repox", "storage").toString,
    connectionTimeout = 6 seconds,
    connectionIdleTimeout = 10 seconds,
    mainClientMaxConnections = 200,
    mainClientMaxConnectionsPerHost = 10,
    proxyClientMaxConnections = 20,
    proxyClientMaxConnectionsPerHost = 10
  )

  val instance: Agent[Config] = Agent[Config](null)

  def set(data: Config): Future[Config] = instance.alter(data)

  def get = instance.get()

  def storage: String = instance.get().storage

  def repos: Seq[Repo] = instance.get().repos

  def enabledRepos: Seq[Repo] = repos.filterNot(_.disabled)

  def proxies: Seq[ProxyServer] = instance.get().proxies

  def enabledProxies: Seq[ProxyServer] = proxies.filterNot(_.disabled)

  def proxyUsage: Map[Repo, ProxyServer] = instance.get().proxyUsage

  def immediate404Rules: Seq[Immediate404Rule] = instance.get().immediate404Rules

  def enabledImmediate404Rules: Seq[Immediate404Rule] = immediate404Rules.filterNot(_.disabled)

  def expireRules: Seq[ExpireRule] = instance.get().expireRules

  def enabledExpireRules: Seq[ExpireRule] = expireRules.filterNot(_.disabled)

  def clientOf(repo: Repo): AsyncHttpClient = instance.get().proxyUsage.get(repo) match {
    case None => Repox.mainClient.get()
    case Some(proxy) =>
      Repox.proxyClients.get().getOrElse(proxy, Repox.mainClient.get())
  }

  def connectionTimeout: Duration = instance.get().connectionTimeout

  def connectionIdleTimeout: Duration = instance.get().connectionIdleTimeout

  def mainClientMaxConnections: Int = instance.get().mainClientMaxConnections

  def mainClientMaxConnectionsPerHost: Int = instance.get().mainClientMaxConnectionsPerHost

  def proxyClientMaxConnections: Int = instance.get().proxyClientMaxConnections

  def proxyClientMaxConnectionsPerHost: Int = instance.get().proxyClientMaxConnectionsPerHost
}
