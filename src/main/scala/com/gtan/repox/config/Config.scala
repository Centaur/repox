package com.gtan.repox.config

import java.nio.file.{Path, Paths}

import akka.agent.Agent
import com.gtan.repox.data._
import com.gtan.repox.Repox
import com.ning.http.client.{AsyncHttpClient, ProxyServer => JProxyServer}
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Properties.userHome

case class Config(proxies: Seq[ProxyServer],
                  repos: IndexedSeq[Repo],
                  connectorUsage: Map[Repo, Connector],
                  proxyUsage: Map[Connector, ProxyServer],
                  immediate404Rules: Seq[Immediate404Rule],
                  expireRules: Seq[ExpireRule],
                  connectors: Set[Connector],
                  headTimeout: Duration,
                  headRetryTimes: Int,
                  password: String,
                  extraResources: Seq[String])

object Config extends LazyLogging with ConfigFormats {

  val defaultProxies                 = List(
    ProxyServer(id = Some(1), name = "Lantern", protocol = JProxyServer.Protocol.HTTP, host = "localhost", port = 8787)
  )
  val defaultConnectors              = Set(
    Connector(id = Some(1),
      name = "default",
      connectionTimeout = 5 seconds,
      connectionIdleTimeout = 10 seconds,
      maxConnections = 40,
      maxConnectionsPerHost = 20),
    Connector(id = Some(2),
      name = "fast-upstream",
      connectionTimeout = 5 seconds,
      connectionIdleTimeout = 5 seconds,
      maxConnections = 40,
      maxConnectionsPerHost = 20
    ),
    Connector(id = Some(3),
      name = "slow-upstream",
      connectionTimeout = 5 seconds,
      connectionIdleTimeout = 40 seconds,
      maxConnections = 40,
      maxConnectionsPerHost = 20
    )
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
    Repo(Some(9), "scalajs", "http://dl.bintray.com/content/scala-js/scala-js-releases", priority = 4),
    Repo(Some(7), "central", "http://repo1.maven.org/maven2", priority = 4, maven = true),
    Repo(Some(8), "ibiblio", "http://mirrors.ibiblio.org/maven2", priority = 5, maven = true)
  )


  val defaultImmediate404Rules: Seq[Immediate404Rule] = Vector(
    Immediate404Rule(Some(1), """.+-javadoc\.jar"""), // we don't want javadoc
    Immediate404Rule(Some(2), """.+-parent.*\.jar"""), // parent have no jar
    Immediate404Rule(Some(3), """(/.+)+/((.+?-project)(_(.+?)(_(.+))?)?)/(.+?)/\3-\8(-(.+?))?\.jar"""), // maven x-project have no jar
    Immediate404Rule(Some(4), """(/.+)+/((.+?-pom)(_(.+?)(_(.+))?)?)/(.+?)/\3-\8(-(.+?))?\.jar"""), // maven x-pom have no jar
    Immediate404Rule(Some(5), """/.+?/(.+?-project)/.+/\1\.jar"""), // ivy x-project have no jar
    Immediate404Rule(Some(6), """/org/jboss/xnio/xnio-all/.+\.jar"""),
    Immediate404Rule(Some(7), """/org\.jboss\.xnio/xnio-all/.+\.jar"""),
    Immediate404Rule(Some(8), """/org/apache/apache/(\d+)/.+\.jar"""),
    Immediate404Rule(Some(9), """/org\.apache/apache/(\d+)/.+\.jar"""),
    Immediate404Rule(Some(10), """/com/google/google/(\d+)/.+\.jar"""),
    Immediate404Rule(Some(11), """/com\.google/google/(\d+)/.+\.jar"""),
    Immediate404Rule(Some(12), """/org/ow2/ow2/.+\.jar"""),
    Immediate404Rule(Some(13), """/org\.ow2/ow2/.+\.jar"""),
    Immediate404Rule(Some(14), """(/.+)+/((.+?-site)(_(.+?)(_(.+))?)?)/(.+?)/\3-\8(-(.+?))?\.jar"""), // maven x-site have no jar
    Immediate404Rule(Some(15), """/.+?/(.+?-site)/.+/\1\.jar"""), // ivy x-site have no jar
    Immediate404Rule(Some(16), """/org/fusesource/leveldbjni/.+-sources\.jar"""),
    Immediate404Rule(Some(17), """/org\.fusesource\.leveldbjni/.+-sources\.jar"""),
    Immediate404Rule(Some(18), """/.+?/(.+?-pom)/.+/\1\.jar"""), // ivy x-pom have no jar
    Immediate404Rule(Some(19), """/org/webjars/.+-sources.jar"""), // webjars have no sources
    Immediate404Rule(Some(20), """/org\.webjars/.+-sources.jar""")
  )

  def defaultExpireRules = Seq(
    ExpireRule(Some(1), ".+/maven-metadata.xml", 1 day)
  )

  implicit class string2Repo(repoName: String) {
    def use(connectorName: String) =
      defaultRepos.find(_.name == repoName).get -> defaultConnectors.find(_.name == connectorName).get
  }

  val default = Config(
    proxies = defaultProxies,
    repos = defaultRepos,
    connectorUsage = Map(
      "koala" use "fast-upstream",
      "typesafe" use "slow-upstream",
      "oschina" use "fast-upstream",
      "scalajs" use "slow-upstream"
    ),
    proxyUsage = Map(),
    immediate404Rules = defaultImmediate404Rules,
    expireRules = defaultExpireRules,
    connectors = defaultConnectors,
    headTimeout = 3 seconds,
    headRetryTimes = 3,
    password = "zhimakaimen",
    extraResources = Seq(Paths.get(userHome, ".m2", "repository").toString)
  )

  val instance: Agent[Config] = Agent[Config](null)

  def set(data: Config): Future[Config] = instance.alter(data)

  def get = instance.get()

  val storagePath = Paths.get(userHome, ".repox", "storage")

  def repos: Seq[Repo] = instance.get().repos

  def enabledRepos: Seq[Repo] = repos.filterNot(_.disabled)

  def proxies: Seq[ProxyServer] = instance.get().proxies

  def enabledProxies: Seq[ProxyServer] = proxies.filterNot(_.disabled)

  def connectorUsage: Map[Repo, Connector] = instance.get().connectorUsage

  def proxyUsage: Map[Connector, ProxyServer] = instance.get().proxyUsage

  def immediate404Rules: Seq[Immediate404Rule] = instance.get().immediate404Rules

  def enabledImmediate404Rules: Seq[Immediate404Rule] = immediate404Rules.filterNot(_.disabled)

  def expireRules: Seq[ExpireRule] = instance.get().expireRules

  def enabledExpireRules: Seq[ExpireRule] = expireRules.filterNot(_.disabled)

  def password: String = instance.get().password

  def headRetryTimes: Int = instance.get().headRetryTimes

  def headTimeout: Duration = instance.get().headTimeout

  def connectors: Set[Connector] = instance.get().connectors

  def extraResources: Seq[String] = instance.get().extraResources

  def resourceBases: Seq[String] = extraResources :+ storagePath.toString
}
