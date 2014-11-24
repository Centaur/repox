package com.gtan.repox

import java.net.URL
import java.nio.file.Paths
import java.util.Date

import akka.actor.ActorSystem
import com.ning.http.client._
import com.ning.http.client.resumable.ResumableIOExceptionFilter
import com.typesafe.scalalogging.LazyLogging
import io.undertow.Handlers
import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.resource.FileResourceManager
import io.undertow.util.{StatusCodes, MimeMappings, Headers, HttpString}

import scala.collection.JavaConverters._
import scala.concurrent.{Future, Promise}
import scala.language.postfixOps


object Repox extends LazyLogging {

  import scala.concurrent.ExecutionContext.Implicits.global

  val system = ActorSystem("repox")

  val storage = Paths.get(System.getProperty("user.home"), ".repox", "storage")

  val upstreams = List(
    Repo("osc", "http://maven.oschina.net/content/groups/public", priority = 1),
    Repo("koala", "http://nexus.openkoala.org/nexus/content/groups/Koala-release", priority = 1),
    Repo("sbt-plugin", "http://dl.bintray.com/sbt/sbt-plugin-releases", priority = 2),
    Repo("typesafe", "http://repo.typesafe.com/typesafe/releases", priority = 2),
    Repo("sonatype", "http://oss.sonatype.org/content/repositories/releases", priority = 2),
    Repo("spray", "http://repo.spray.io"),
    Repo("scalaz", "http://dl.bintray.com/scalaz/releases"),
    Repo("uk", "http://uk.maven.org/maven2", priority = 5),
    Repo("central", "http://repo1.maven.org/maven2", priority = 5)
  )

  val immediat404Rules = List(
    Immediate404Rule( """.+-javadoc.jar"""), // we don't want javadoc
    Immediate404Rule( """.+-parent.*.jar"""), // parent have no jar
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
    .setMaximumConnectionsTotal(100)
    .setIdleConnectionInPoolTimeoutInMs(Int.MaxValue)
    .setIdleConnectionTimeoutInMs(Int.MaxValue)
    .setFollowRedirects(true)
    .build()
  )
  val resourceManager = new FileResourceManager(storage.toFile, 100 * 1024)

  type StatusCode = Int
  type ResponseHeaders = Map[String, java.util.List[String]]
  type HeaderResponse = (Repo, StatusCode, ResponseHeaders)



  def handle(exchange: HttpServerExchange): Unit = {
    val uri = exchange.getRequestURI
    val resolvedPath = storage.resolve(uri.tail)

    exchange.getRequestMethod.toString.toUpperCase match {
      case "HEAD" =>
        val file = resolvedPath.toFile
        if (file.exists()) {
          logger.debug(s"Direct HEAD $uri")
          val resource = resourceManager.getResource(uri)
          exchange.setResponseCode(StatusCodes.NO_CONTENT)
          val headers = exchange.getResponseHeaders
          headers.put(Headers.CONTENT_TYPE, file.length())
            .put(Headers.SERVER, "repox")
            .put(Headers.CONNECTION, Headers.KEEP_ALIVE.toString)
            .put(Headers.CONTENT_TYPE, resource.getContentType(MimeMappings.DEFAULT))
            .put(Headers.LAST_MODIFIED, resource.getLastModifiedString)
          exchange.endExchange()
        } else {
          new HeadWorker(exchange).`try`(times = 3)
        }
      case "GET" =>
        if (resolvedPath.toFile.exists()) {
          logger.debug(s"$uri Already downloaded. Serve immediately.")
          Handlers.resource(resourceManager).handleRequest(exchange)
        } else {
          val candidates = upstreams.groupBy(_.priority).toList.sortBy(_._1).map(_._2)
          logger.debug("Start download....")
          val filtered =
            if (!uri.endsWith(".jar"))
              candidates.tail
            else candidates
          GetMaster.run(exchange, resolvedPath, filtered)
        }
    }
  }

}
