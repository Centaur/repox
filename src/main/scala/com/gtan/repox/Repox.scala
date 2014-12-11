package com.gtan.repox

import java.io.IOException
import java.nio.file.Paths

import akka.actor.{ActorSystem, Props}
import akka.agent.Agent
import com.gtan.repox.config.{ConfigView, ConfigPersister, Config}
import com.gtan.repox.data.{ExpireRule, ProxyServer, Repo}
import com.ning.http.client.{ProxyServer => JProxyServer, AsyncHttpClientConfig, AsyncHttpClient}
import com.typesafe.scalalogging.LazyLogging
import io.undertow.Handlers
import io.undertow.io.{Sender, IoCallback}
import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.resource.FileResourceManager
import io.undertow.util._

import scala.language.postfixOps
import scala.concurrent.duration._

object Repox extends LazyLogging {
  def lookForExpireRule(uri: String): Option[ExpireRule] = Config.expireRules.find(rule => !rule.disabled && uri.matches(rule.pattern))


  import concurrent.ExecutionContext.Implicits.global

  val system = ActorSystem("repox")

  val configView = system.actorOf(Props[ConfigView], name = "ConfigView")
  val configPersister = system.actorOf(Props[ConfigPersister], "ConfigPersister")
  val expirationPersister = system.actorOf(Props[ExpirationPersister], "ExpirationPersister")
  val head404Cache = system.actorOf(Props[Head404Cache], "HeaderCache")
  val requestQueueMaster = system.actorOf(Props[RequestQueueMaster], "RequestQueueMaster")
  val sha1Checker = system.actorOf(Props[SHA1Checker], "SHA1Checker")


  val clients: Agent[Map[String, AsyncHttpClient]] = Agent(null)

  def resourceManager = new FileResourceManager(Paths.get(Config.storage).toFile, Long.MaxValue)

  type StatusCode = Int
  type ResponseHeaders = Map[String, java.util.List[String]]
  type HeaderResponse = (Repo, StatusCode, ResponseHeaders)

  implicit val timeout = akka.util.Timeout(1 second)

  def isIvyUri(uri: String) = uri.matches( """/[^/]+?\.[^/]+?/.+""")

  def resolveToPath(uri: String) = Paths.get(Config.storage).resolve(uri.tail)

  def orderByPriority(candidates: Seq[Repo]): Seq[Seq[Repo]] =
    candidates.groupBy(_.priority).toSeq.sortBy(_._1).map(_._2)

  def respond404(exchange: HttpServerExchange): Unit = {
    exchange.setResponseCode(StatusCodes.NOT_FOUND)
    exchange.getResponseChannel // just to avoid mysterious setting Content-length to 0 in endExchange, ugly
    exchange.endExchange()
  }

  def immediate404(exchange: HttpServerExchange): Unit = {
    logger.info(s"Immediate 404 ${exchange.getRequestURI}.")
    respond404(exchange)
  }

  /**
   * this is the one and only truth
   * @param uri resource to get or query
   * @return
   */
  def downloaded(uri: String): Boolean = {
    Paths.get(Config.storage).resolve(uri.tail).toFile.exists
  }

  lazy val resourceHandler = Handlers.resource(resourceManager)

  def sendFile(exchange: HttpServerExchange): Unit = {
    resourceHandler.handleRequest(exchange)
  }

  def immediateFile(exchange: HttpServerExchange): Unit = {
    logger.debug(s"Immediate file ${exchange.getRequestURI}")
    sendFile(exchange)
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
      case _ =>
        immediate404(exchange)
    }
  }

  def init(): Unit = {
    configPersister ! 'LoadConfig // this does nothing but eagerly init Repox
  }
}
