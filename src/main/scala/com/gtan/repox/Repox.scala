package com.gtan.repox

import java.io.IOException
import java.nio.file.Paths

import akka.actor.{ActorSystem, Props}
import akka.agent.Agent
import com.gtan.repox.config.{ConfigView, ConfigPersister, Config}
import com.gtan.repox.data.{Connector, ExpireRule, ProxyServer, Repo}
import com.ning.http.client.{ProxyServer => JProxyServer, AsyncHttpClientConfig, AsyncHttpClient}
import com.typesafe.scalalogging.LazyLogging
import io.undertow.Handlers
import io.undertow.io.{Sender, IoCallback}
import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.resource.{ResourceManager, ResourceHandler, FileResourceManager}
import io.undertow.util._

import scala.language.postfixOps
import scala.concurrent.duration._
import scala.util.{Success, Failure, Try}

object Repox extends LazyLogging {
  def lookForExpireRule(uri: String): Option[ExpireRule] = Config.expireRules.find(rule => !rule.disabled && uri.matches(rule.pattern))


  import concurrent.ExecutionContext.Implicits.global

  val system = ActorSystem("repox")

  val configView = system.actorOf(Props[ConfigView], name = "ConfigView")
  val configPersister = system.actorOf(Props[ConfigPersister], "ConfigPersister")
  val expirationPersister = system.actorOf(Props[ExpirationPersister], "ExpirationPersister")
  val head404Cache = system.actorOf(Props[Head404Cache], "HeaderCache")
  val requestQueueMaster = system.actorOf(Props[RequestQueueMaster], "RequestQueueMaster")


  val clients: Agent[Map[String, AsyncHttpClient]] = Agent(null)

  def clientOf(repo: Repo): (Connector, AsyncHttpClient) = Config.connectorUsage.get(repo) match {
    case None =>
      Config.connectors.find(_.name == "default").get -> clients.get().apply("default")
    case Some(connector) =>
      connector -> clients.get().apply(connector.name)
  }

  val storageManager = new FileResourceManager(Config.storagePath.toFile, 100 * 1024)

  type StatusCode = Int
  type ResponseHeaders = Map[String, java.util.List[String]]
  type HeaderResponse = (Repo, StatusCode, ResponseHeaders)

  implicit val timeout = akka.util.Timeout(1 second)

  def isIvyUri(uri: String) = uri.matches( """/[^/]+?\.[^/]+?/.+""")

  def resolveToPaths(uri: String) = (Config.storagePath.resolve(uri.tail), Config.storagePath.resolve(uri.tail + ".sha1"))

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

  def smart404(exchange: HttpServerExchange): Unit = {
    logger.info(s"Smart 404 ${exchange.getRequestURI}.")
    respond404(exchange)
  }

  /**
   * this is the one and only truth
   * @param uri resource to get or query
   * @return
   */
  def downloaded(uri: String): Option[(ResourceManager, ResourceHandler)] = {
    resourceHandlers.get().find { case (resourceManager, handler) =>
      resourceManager.getResource(uri.tail) != null
    }
  }

  val MavenFormat = """(/.+)+/((.+?)(_(.+?)(_(.+))?)?)/(.+?)/(\3-\8(-(.+?))?\.(.+))""".r
  val IvyFormat = """/(.+?)/(.+?)/(scala_(.+?)/)?(sbt_(.+?)/)?(.+?)/(.+?)s/((.+?)(-(.+))?\.(.+))""".r
  val supportedScalaVersion = List("2.10", "2.11")
  val supportedSbtVersion = List("0.13")

  /**
   * transform between uri formats
   * @param uri
   * @return maven format if is ivy format, or ivy format if is maven format
   */
  def peer(uri: String): Try[List[String]] = uri match {
    case MavenFormat(groupIds, _, artifactId, _, scalaVersion, _, sbtVersion, version, fileName, _, classifier, ext) =>
      val organization = groupIds.split("/").filter(_.nonEmpty).mkString(".")
      val typ = ext match {
        case "pom" => "ivy"
        case _ => "jar"
      }
      val peerFile = ext match {
        case "pom" => "ivy.xml"
        case _ => s"$artifactId.$ext"
      }
      val result = if (scalaVersion != null && sbtVersion != null) {
        s"/$organization/$artifactId/scala_$scalaVersion/sbt_$sbtVersion/$version/${typ}s/$peerFile" :: Nil
      } else if (scalaVersion == null && sbtVersion == null) {
        val guessedMavenArtifacts = for (scala <- supportedScalaVersion; sbt <- supportedSbtVersion) yield
          s"$groupIds/${artifactId}_${scala}_$sbt/$version/$fileName"
        s"/$organization/$artifactId/$version/${typ}s/$peerFile" :: guessedMavenArtifacts
      } else List(s"/$organization/$artifactId/$version/${typ}s/$peerFile")
      Success(result)
    case IvyFormat(organization, module, _, scalaVersion, _, sbtVersion, revision, typ, fileName, artifact, _, classifier, ext) =>
      val result = if (scalaVersion == null && sbtVersion == null) {
        for (scala <- supportedScalaVersion; sbt <- supportedSbtVersion) yield
          s"/${organization.split("\\.").mkString("/")}/${module}_${scala}_$sbt/$revision/$module-$revision.$ext"
      } else Nil
      Success(result)
    case _ =>
      Failure(new RuntimeException("Invalid Request"))
  }

  val resourceHandlers: Agent[Map[ResourceManager, ResourceHandler]] = Agent(null)

  def sendFile(resourceHandler: ResourceHandler, exchange: HttpServerExchange): Unit = {
    resourceHandler.handleRequest(exchange)
  }

  def immediateFile(resourceHandler: ResourceHandler, exchange: HttpServerExchange): Unit = {
    logger.debug(s"Immediate file ${exchange.getRequestURI}")
    sendFile(resourceHandler, exchange)
  }

  def respondHead(exchange: HttpServerExchange, headers: ResponseHeaders): Unit = {
    exchange.setResponseCode(StatusCodes.NO_CONTENT)
    val target = exchange.getResponseHeaders
    for ((k, v) <- headers)
      target.putAll(new HttpString(k), v)
    exchange.getResponseChannel // just to avoid mysterious setting Content-length to 0 in endExchange, ugly
    exchange.endExchange()
  }

  def immediateHead(resourceManager: ResourceManager, exchange: HttpServerExchange): Unit = {
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
    val uri = exchange.getRequestURI
    val method = exchange.getRequestMethod
    Repox.peer(uri) match {
      case Success(_) =>
         method match {
          case Methods.HEAD =>
            requestQueueMaster ! Requests.Head(exchange)
          case Methods.GET =>
            requestQueueMaster ! Requests.Get(exchange)
          case _ =>
            immediate404(exchange)
        }
      case Failure(_) =>
        Repox.respond404(exchange)
        logger.debug(s"Invalid request $method $uri. 404.")
    }
  }

  def init(): Unit = {
    configPersister ! 'LoadConfig // this does nothing but eagerly init Repox
  }
}
