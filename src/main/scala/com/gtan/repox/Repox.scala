package com.gtan.repox

import java.net.URL
import java.nio.file.Paths

import akka.actor.ActorSystem
import com.ning.http.client.AsyncHandler.STATE
import com.ning.http.client._
import com.ning.http.client.resumable.ResumableIOExceptionFilter
import com.typesafe.scalalogging.LazyLogging
import io.undertow.Handlers
import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.resource.FileResourceManager
import io.undertow.util.HttpString

import scala.collection.JavaConverters._
import scala.concurrent.{Future, Promise}
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
 * Created by xf on 14/11/20.
 */
object Repox extends LazyLogging {

  import scala.concurrent.ExecutionContext.Implicits.global

  val system = ActorSystem("repox")

  val storage = Paths.get(System.getProperty("user.home"), ".repox", "storage")

  val upstreams = List(
    Repo("osc", "http://maven.oschina.net/content/groups/public", priority = 1),
    Repo("sbt-plugin", "https://dl.bintray.com/sbt/sbt-plugin-releases", priority = 2),
    Repo("typesafe", "https://repo.typesafe.com/typesafe/releases", priority = 2),
    Repo("sonatype", "https://oss.sonatype.org/content/repositories/releases", priority = 2),
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
    Immediate404Rule( """/org/fusesource/leveldbjni/.+-sources.jar"""),
    Immediate404Rule( """/org\.fusesource\.leveldbjni/.+-sources.jar"""),
    Immediate404Rule( """.*/jsr305.*\-sources.jar""")
  )
  val client = new AsyncHttpClient(new AsyncHttpClientConfig.Builder()
    .addIOExceptionFilter(new ResumableIOExceptionFilter())
    .setRequestTimeoutInMs(Int.MaxValue)
    .setConnectionTimeoutInMs(6000)
    .setAllowPoolingConnection(true)
    .setIdleConnectionInPoolTimeoutInMs(600000)
    .setFollowRedirects(true)
    .build()
  )

  type StatusCode = Int
  type ResponseHeaders = Map[String, java.util.List[String]]
  type Upstream = Repo
  type HeaderResponse = (Upstream, StatusCode, ResponseHeaders)

  def headWorker(exchange: HttpServerExchange, upstream: Repo): Future[HeaderResponse] = {
    val upstreamUrl = upstream.base + exchange.getRequestURI
    val promise = Promise[HeaderResponse]()
    var statusCode = 200

    val requestHeaders = new FluentCaseInsensitiveStringsMap()
    for (name <- exchange.getRequestHeaders.getHeaderNames.asScala) {
      requestHeaders.add(name.toString, exchange.getRequestHeaders.get(name))
    }
    val upstreamHost = new URL(upstreamUrl).getHost
    requestHeaders.put("Host", List(upstreamHost).asJava)
    requestHeaders.put("Accept-Encoding", List("identity").asJava)

    client.prepareHead(upstreamUrl)
      .setHeaders(requestHeaders)
      .execute(new AsyncHandler[Unit] with LazyLogging{
      override def onThrowable(t: Throwable): Unit = promise.failure(t)

      override def onCompleted(): Unit = {
        /* we don't get here actually */
      }

      override def onBodyPartReceived(bodyPart: HttpResponseBodyPart): STATE = {
        // we don't get here actually
        STATE.ABORT
      }

      override def onStatusReceived(responseStatus: HttpResponseStatus): STATE = {
        if (!promise.isCompleted) {
          statusCode = responseStatus.getStatusCode
          STATE.CONTINUE
        } else STATE.ABORT
      }

      override def onHeadersReceived(headers: HttpResponseHeaders): STATE = {
        if (!promise.isCompleted) {
          import scala.collection.JavaConverters._
          promise.success(upstream, statusCode, mapAsScalaMapConverter(headers.getHeaders).asScala.toMap)
        }
        STATE.ABORT
      }
    })

    import scala.concurrent.duration._
    TimeoutableFuture(promise.future, after = 3 seconds, name = upstreamHost)
  }

  private def immediat404(uri: String): Boolean = {
    immediat404Rules.exists(_.matches(uri))
  }

  def handle(exchange: HttpServerExchange): Unit = {
    val uri = exchange.getRequestURI

    def tryHead(times: Int): Unit = {
      if (immediat404(uri)) {
        logger.debug(s"$uri immediat 404")
        exchange.setResponseCode(404)
        exchange.endExchange()
      } else if (times == 0) {
        logger.debug(s"Tried 3 times. Give up.")
        exchange.setResponseCode(404)
        exchange.endExchange()
      } else {
        val firstSuccess = Future.find(upstreams.map(upstream => headWorker(exchange, upstream)))(_._2 == 200)

        firstSuccess.onComplete {
          case Success(Some(Tuple3(upstream, statusCode, headers))) =>
            logger.info(s"200 HEAD in ${upstream.name} for $uri")
            exchange.setResponseCode(204) // no content in body
            for ((k, v) <- headers) {
              exchange.getResponseHeaders.addAll(new HttpString(k), v.asScala.distinct.asJava)
            }
            exchange.getResponseHeaders.add(new HttpString("Data-Source"), upstream.name)
            exchange.getResponseChannel // just to avoid mysterious setting Content-length to 0 in endExchange, ugly
            exchange.endExchange()
          case Success(None) | Failure(_) =>
            logger.info(s"! No headers found for ${exchange.getRequestURI} ")
            tryHead(times - 1)
        }
      }
    }
    exchange.getRequestMethod.toString.toUpperCase match {
      case "HEAD" =>
        tryHead(times = 3)
      case "GET" =>
        val uri = exchange.getRequestURI
        val resolvedPath = storage.resolve(uri.tail)
        if (resolvedPath.toFile.exists()) {
          logger.debug(s"$uri Already downloaded. Serve immediately.")
          Handlers.resource(new FileResourceManager(storage.toFile, 100 * 1024)).handleRequest(exchange)
        } else {
          val candidates = upstreams.groupBy(_.priority).toList.sortBy(_._1).map(_._2)
          logger.debug("Start download....")
          val filtered =
            if (uri.endsWith("ivy.xml") || uri.endsWith("ivy.xml.sha1"))
              candidates.tail
            else candidates
          GetMaster.run(exchange, resolvedPath, filtered)
        }
    }
  }

}
