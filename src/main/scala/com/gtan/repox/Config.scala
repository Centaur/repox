package com.gtan.repox

import java.nio.file.{Paths, Path}

import akka.agent.Agent
import com.ning.http.client.{AsyncHttpClient, ProxyServer}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * Created by IntelliJ IDEA.
 * User: xf
 * Date: 14/11/30
 * Time: 下午3:10
 */


case class Config(proxies: Map[String, ProxyServer],
                  repos: Vector[Repo],
                  proxyUsage: Map[Repo, ProxyServer],
                  immediate404Rules: Vector[Immediate404Rule],
                  storage: Path,
                  connectionTimeout: Duration,
                  connectionIdleTimeout: Duration,
                  mainClientMaxConnectionsPerHost: Int,
                  mainClientMaxConnections: Int,
                  proxyClientMaxConnectionsPerHost: Int,
                  proxyClientMaxConnections: Int)

object Config {
  val defaultProxies = Map(
    "lantern" -> new ProxyServer("localhost", 8787)
  )
  val defaultRepos = Vector(
    Repo("koala", "http://nexus.openkoala.org/nexus/content/groups/Koala-release",
      priority = 1, getOnly = true, maven = true),
    Repo("sonatype", "http://oss.sonatype.org/content/repositories/releases", priority = 2),
    Repo("typesafe", "http://repo.typesafe.com/typesafe/releases", priority = 2),
    Repo("oschina", "http://maven.oschina.net/content/groups/public",
      priority = 2, getOnly = true, maven = true),
    Repo("sbt-plugin", "http://dl.bintray.com/sbt/sbt-plugin-releases", priority = 4),
    Repo("scalaz", "http://dl.bintray.com/scalaz/releases", priority = 4),
    Repo("central", "http://repo1.maven.org/maven2", priority = 4, maven = true),
    Repo("ibiblio", "http://mirrors.ibiblio.org/maven2", priority = 5, maven = true)
  )
  val defaultImmediate404Rules = Vector(
    Immediate404Rule( """.+-javadoc\.jar"""), // we don't want javadoc
    Immediate404Rule( """.+-parent.*\.jar"""), // parent have no jar
    Immediate404Rule( """/org/scala-sbt/.*""", exclude = Some( """/org/scala-sbt/test-interface/.*""")), // ivy only artifact have no maven uri
    //    Immediat404Rule( """/org/scala-tools/.*"""), // ivy only artifact have no maven uri
    Immediate404Rule( """/com/eed3si9n/.*"""), // ivy only artifact have no maven uri
    Immediate404Rule( """/io\.spray/.*""", exclude = Some( """/io\.spray/sbt-revolver.*""")), // maven only artifact have no ivy uri
    Immediate404Rule( """/org/jboss/xnio/xnio-all/.+\.jar"""),
    Immediate404Rule( """/org\.jboss\.xnio/xnio-all/.+\.jar"""),
    Immediate404Rule( """/org/apache/apache/(\d+)/.+\.jar"""),
    Immediate404Rule( """/org\.apache/apache/(\d+)/.+\.jar"""),
    Immediate404Rule( """/com/google/google/(\d+)/.+\.jar"""),
    Immediate404Rule( """/com\.google/google/(\d+)/.+\.jar"""),
    Immediate404Rule( """/org/ow2/ow2/.+\.jar"""),
    Immediate404Rule( """/org\.ow2/ow2/.+\.jar"""),
    Immediate404Rule( """/com/github/mpeltonen/sbt-idea/.*\.jar"""),
    Immediate404Rule( """/com\.github\.mpeltonen/sbt-idea/.*\.jar"""),
    Immediate404Rule( """/org/fusesource/leveldbjni/.+-sources\.jar"""),
    Immediate404Rule( """/org\.fusesource\.leveldbjni/.+-sources\.jar"""),
    Immediate404Rule( """.*/jsr305.*\-sources\.jar""")
  )

  val default = Config(
    proxies = defaultProxies,
    repos = defaultRepos,
    proxyUsage = Map(defaultRepos.find(_.name == "typesafe").get -> defaultProxies("lantern")),
    immediate404Rules = defaultImmediate404Rules,
    storage = Paths.get(System.getProperty("user.home"), ".repox", "storage"),
    connectionTimeout = 6 seconds,
    connectionIdleTimeout = 10 seconds,
    mainClientMaxConnections = 200,
    mainClientMaxConnectionsPerHost = 10,
    proxyClientMaxConnections = 10,
    proxyClientMaxConnectionsPerHost = 20
  )

  val instance: Agent[Config] = Agent[Config](null)

  def set(data: Config): Future[Config] = instance.alter(data)

  def get = instance.get()

  def storage: Path = instance.get().storage

  def repos: Vector[Repo] = instance.get().repos

  def proxies: Map[String, ProxyServer] = instance.get().proxies

  def immediate404Rules: Vector[Immediate404Rule] = instance.get().immediate404Rules

  def clientOf(repo: Repo): AsyncHttpClient = instance.get().proxyUsage.get(repo) match {
    case None => Repox.mainClient
    case Some(proxy) => Repox.proxyClients(proxy)
  }

  def connectionTimeout: Duration = instance.get().connectionTimeout

  def connectionIdleTimeout: Duration = instance.get().connectionIdleTimeout

  def mainClientMaxConnections: Int = instance.get().mainClientMaxConnections

  def mainClientMaxConnectionsPerHost: Int = instance.get().mainClientMaxConnectionsPerHost

  def proxyClientMaxConnections: Int = instance.get().proxyClientMaxConnections

  def proxyClientMaxConnectionsPerHost: Int = instance.get().proxyClientMaxConnectionsPerHost
}
