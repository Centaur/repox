package com.gtan.repox

import java.nio.file.Paths

import akka.actor.{ActorSystem, Props}
import com.ning.http.client._
import com.ning.http.client.resumable.ResumableIOExceptionFilter
import com.typesafe.scalalogging.LazyLogging
import io.undertow.Handlers
import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.resource.FileResourceManager
import io.undertow.util._

import scala.language.postfixOps

object Repox extends LazyLogging {

  val system = ActorSystem("repox")

  val storage = Paths.get(System.getProperty("user.home"), ".repox", "storage")

  val upstreams = List(
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

  val immediat404Rules = List(
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
  val client = new AsyncHttpClient(new AsyncHttpClientConfig.Builder()
    .addIOExceptionFilter(new ResumableIOExceptionFilter())
    .setRequestTimeoutInMs(Int.MaxValue)
    .setConnectionTimeoutInMs(6000)
    .setAllowPoolingConnection(true)
    .setAllowSslConnectionPool(true)
    .setMaximumConnectionsPerHost(10)
    .setMaximumConnectionsTotal(200)
    .setIdleConnectionInPoolTimeoutInMs(10000)
    .setIdleConnectionTimeoutInMs(10000)
    .setFollowRedirects(true)
    .build()
  )
  val proxyClient = new AsyncHttpClient(new AsyncHttpClientConfig.Builder()
    .addIOExceptionFilter(new ResumableIOExceptionFilter())
    .setRequestTimeoutInMs(Int.MaxValue)
    .setConnectionTimeoutInMs(6000)
    .setAllowPoolingConnection(true)
    .setAllowSslConnectionPool(true)
    .setMaximumConnectionsPerHost(10)
    .setMaximumConnectionsTotal(20)
    .setProxyServer(new ProxyServer("127.0.0.1", 8787))
    .setIdleConnectionInPoolTimeoutInMs(10000)
    .setIdleConnectionTimeoutInMs(10000)
    .setFollowRedirects(true)
    .build()
  )

  val resourceManager = new FileResourceManager(storage.toFile, 100 * 1024)

  type StatusCode = Int
  type ResponseHeaders = Map[String, java.util.List[String]]
  type HeaderResponse = (Repo, StatusCode, ResponseHeaders)


  val head404Cache = system.actorOf(Props[Head404Cache], "HeaderCache")
  val requestQueueMaster = system.actorOf(Props[RequestQueueMaster], "RequestQueueMaster")


  import scala.concurrent.duration._

  implicit val timeout = akka.util.Timeout(1 second)

  def isIvyUri(uri: String) = uri.matches( """/[^/]+?\.[^/]+?/.+""")

  def resolveToPath(uri: String) = Repox.storage.resolve(uri.tail)

  def orderByPriority(candidates: List[Repo]): List[List[Repo]] =
    candidates.groupBy(_.priority).toList.sortBy(_._1).map(_._2)

  def respond404(exchange: HttpServerExchange): Unit = {
    exchange.setResponseCode(StatusCodes.NOT_FOUND)
    exchange.endExchange()
  }

  def immediate404(exchange: HttpServerExchange): Unit = {
    respond404(exchange)
    logger.info(s"Immediate 404 ${exchange.getRequestURI}.")
  }

  /**
   * this is the one and only truth
   * @param uri resource to get or query
   * @return
   */
  def downloaded(uri: String): Boolean = {
    storage.resolve(uri.tail).toFile.exists
  }

  def immediateFile(exchange: HttpServerExchange): Unit = {
    Handlers.resource(resourceManager).handleRequest(exchange)
    logger.debug(s"Immediate file ${exchange.getRequestURI}.")
  }

  def respondHead(exchange: HttpServerExchange, headers: ResponseHeaders): Unit = {
    exchange.setResponseCode(StatusCodes.NO_CONTENT)
    val target = exchange.getResponseHeaders
    for ((k, v) <- headers)
      target.putAll(new HttpString(k), v)
    exchange.getResponseChannel // just to avoid mysterious setting Content-length to 0 in endExchange, ugly
    exchange.endExchange()
  }

  def immediateHead(exchange: HttpServerExchange): Unit = {
    val uri = exchange.getRequestURI
    val resource = resourceManager.getResource(uri)
    exchange.setResponseCode(StatusCodes.NO_CONTENT)
    val headers = exchange.getResponseHeaders
    headers.put(Headers.CONTENT_LENGTH, resource.getContentLength)
      .put(Headers.SERVER, "repox")
      .put(Headers.CONNECTION, Headers.KEEP_ALIVE.toString)
      .put(Headers.CONTENT_TYPE, resource.getContentType(MimeMappings.DEFAULT))
      .put(Headers.LAST_MODIFIED, resource.getLastModifiedString)
    exchange.getResponseChannel // just to avoid mysterious setting Content-length to 0 in endExchange, ugly
    exchange.endExchange()
    logger.debug(s"Immediate head $uri. ")
  }

  def handle(exchange: HttpServerExchange): Unit = {
    exchange.getRequestMethod match {
      case Methods.HEAD =>
        requestQueueMaster ! Requests.Head(exchange)
      case Methods.GET =>
        requestQueueMaster ! Requests.Get(exchange)
    }
  }

}
