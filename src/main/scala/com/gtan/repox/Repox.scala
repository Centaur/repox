package com.gtan.repox

import java.nio.file.Paths

import akka.actor.{ActorSystem, Props}
import com.gtan.repox.config.{ConfigView, ConfigPersister, Config}
import com.ning.http.client._
import com.ning.http.client.resumable.ResumableIOExceptionFilter
import com.typesafe.scalalogging.LazyLogging
import io.undertow.Handlers
import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.resource.FileResourceManager
import io.undertow.util._

import scala.language.postfixOps
import scala.concurrent.duration._

object Repox extends LazyLogging {

  val system = ActorSystem("repox")

  val configView = Repox.system.actorOf(Props[ConfigView], name = "ConfigView")
  val head404Cache = system.actorOf(Props[Head404Cache], "HeaderCache")
  val requestQueueMaster = system.actorOf(Props[RequestQueueMaster], "RequestQueueMaster")
  val sha1Checker = system.actorOf(Props[SHA1Checker], "SHA1Checker")

  def loadConfig(): Unit = {
    system.actorOf(Props[ConfigPersister], "ConfigPersister")
  }

  lazy val mainClient = new AsyncHttpClient(new AsyncHttpClientConfig.Builder()
    .setRequestTimeoutInMs(Int.MaxValue)
    .setConnectionTimeoutInMs(Config.connectionTimeout.toMillis.toInt)
    .setAllowPoolingConnection(true)
    .setAllowSslConnectionPool(true)
    .setMaximumConnectionsPerHost(Config.mainClientMaxConnectionsPerHost)
    .setMaximumConnectionsTotal(Config.mainClientMaxConnections)
    .setIdleConnectionInPoolTimeoutInMs(Config.connectionIdleTimeout.toMillis.toInt)
    .setIdleConnectionTimeoutInMs(Config.connectionIdleTimeout.toMillis.toInt)
    .setFollowRedirects(true)
    .build()
  )
  lazy val proxyClients = (for (proxy <- Config.proxies) yield {
    proxy -> new AsyncHttpClient(new AsyncHttpClientConfig.Builder()
      .setRequestTimeoutInMs(Int.MaxValue)
      .setConnectionTimeoutInMs(Config.connectionTimeout.toMillis.toInt)
      .setAllowPoolingConnection(true)
      .setAllowSslConnectionPool(true)
      .setMaximumConnectionsPerHost(Config.proxyClientMaxConnectionsPerHost)
      .setMaximumConnectionsTotal(Config.proxyClientMaxConnections)
      .setProxyServer(proxy.toJava)
      .setIdleConnectionInPoolTimeoutInMs(Config.connectionIdleTimeout.toMillis.toInt)
      .setIdleConnectionTimeoutInMs(Config.connectionIdleTimeout.toMillis.toInt)
      .setFollowRedirects(true)
      .build()
    )
  }) toMap

  def resourceManager = new FileResourceManager(Config.storage.toFile, 100 * 1024)

  type StatusCode = Int
  type ResponseHeaders = Map[String, java.util.List[String]]
  type HeaderResponse = (Repo, StatusCode, ResponseHeaders)

  implicit val timeout = akka.util.Timeout(1 second)

  def isIvyUri(uri: String) = uri.matches( """/[^/]+?\.[^/]+?/.+""")

  def resolveToPath(uri: String) = Config.storage.resolve(uri.tail)

  def orderByPriority(candidates: Seq[Repo]): Seq[Seq[Repo]] =
    candidates.groupBy(_.priority).toSeq.sortBy(_._1).map(_._2)

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
    Config.storage.resolve(uri.tail).toFile.exists
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
