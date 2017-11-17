package com.gtan.repox

import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicLong

import akka.actor.{ActorSystem, Props}
import akka.agent.Agent
import com.gtan.repox.config.{Config, ConfigPersister, ConfigQuery}
import com.gtan.repox.data.{Connector, ExpireRule, Repo}
import com.ning.http.client.{AsyncHttpClient, ProxyServer => JProxyServer}
import com.typesafe.scalalogging.LazyLogging
import io.undertow.Handlers
import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.resource.{FileResourceManager, ResourceHandler, ResourceManager}
import io.undertow.util._

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

object Repox extends LazyLogging with HttpHelpers {
  def lookForExpireRule(uri: String): Option[ExpireRule] = Config.expireRules.find(
    rule => !rule.disabled && uri.matches(rule.pattern))


  import concurrent.ExecutionContext.Implicits.global

  val system = ActorSystem("repox")

  private[this] val idGenerator = new AtomicLong(1)

  def nextId: Long = idGenerator.getAndIncrement()

  // uncomment his to view config data modification history
  //  val configQuery = new ConfigQuery(system)
  val configPersister = system.actorOf(Props[ConfigPersister], "ConfigPersister")
  val expirationPersister = system.actorOf(Props[ExpirationManager], "ExpirationPersister")
  val head404Cache = system.actorOf(Props[Head404Cache], "HeaderCache")
  val requestQueueMaster = system.actorOf(Props[RequestQueueMaster], "RequestQueueMaster")


  val clients: Agent[Map[String, AsyncHttpClient]] = Agent(null)

  def clientOf(repo: Repo): (Connector, AsyncHttpClient) = Config.connectorUsage.find {
    case (r, connector) => r.id == repo.id
  } match {
    case None =>
      Config.connectors.find(_.name == "default").get -> clients.get().apply("default")
    case Some(Tuple2(r, connector)) =>
      println(s"Using client of ${connector.name} for ${repo.name}")
      connector -> clients.get().apply(connector.name)
  }

  def initClients() = Repox.clients.alter(Config.connectors.map { connector =>
    connector.name -> connector.createClient
  }.toMap)

  def initResourceManagers() = {
    val storage = Repox.storageManager -> Handlers.resource(Repox.storageManager)
    val (valid, invalid) = Config.resourceBases.partition { base =>
      Paths.get(base).toFile.exists()
    }
    if (invalid.nonEmpty) {
      logger.debug(s"Excluded invalid base(s) (${invalid.mkString(",")})")
    }
    val extra = for (rb <- valid) yield {
      val resourceManager: FileResourceManager = new FileResourceManager(Paths.get(rb).toFile, 100 * 1024)
      val resourceHandler = Handlers.resource(resourceManager)
      resourceManager -> resourceHandler
    }

    Repox.resourceHandlers.alter((extra :+ storage).toMap)
  }

  val storageManager = new FileResourceManager(Config.storagePath.toFile, 100 * 1024)

  implicit val timeout = akka.util.Timeout(1 second)

  def isIvyUri(uri: String) = uri.matches( """/[^/]+?\.[^/]+?/.+""")

  def resolveToPaths(uri: String) = {
    val removeSlashesAtBeginning: String = uri.dropWhile('/' ==)
    (Config.storagePath.resolve(removeSlashesAtBeginning), Config.storagePath.resolve(removeSlashesAtBeginning + ".sha1"))
  }

  def orderByPriority(candidates: Seq[Repo]): Seq[Seq[Repo]] =
    candidates.groupBy(_.priority).toSeq.sortBy(_._1).map(_._2)


  /**
    * this is the one and only truth
    *
    * @param uri resource to get or query
    * @return
    */
  def downloaded(uri: String): Option[(ResourceManager, ResourceHandler)] = {
    resourceHandlers.get().find { case (resourceManager, handler) =>
      resourceManager.getResource(uri.tail) != null
    }
  }

  private[repox] val MavenFormat = """(/.+)+/((.+?)(_(.+?)(_(.+))?)?)/(.+?)/(\3-\8(-(.+?))?\.(.+))""".r
  private[repox] val IvyFormat = """/(.+?)/(.+?)/(scala_(.+?)/)?(sbt_(.+?)/)?(.+?)/(.+?)s/((.+?)(-(.+))?\.(.+))""".r
  private[this] val MetaDataFormat = """.+/maven-metadata\.xml""".r
  private[this] val MD5Request = """.+\.md5""".r
  private[this] val SHA1Request = """(.+)\.sha1""".r
  private[this] val supportedScalaVersion = List("2.10", "2.11", "2.12")
  private[this] val supportedSbtVersion = List("0.13")

  /**
    * transform between uri formats
    *
    * @param uri
    * @return maven format if is ivy format, or ivy format if is maven format
    */
  def peer(uri: String): Try[List[String]] = uri match {
    case MD5Request() =>
      Failure(new RuntimeException("We do not support md5 checksum now."))
    case SHA1Request(prefix) =>
      peer(prefix).map(_.map(_ + ".sha1"))
    case MetaDataFormat() =>
      if (uri.toUpperCase.endsWith("SNAPSHOT/MAVEN-METADATA.XML"))
        Failure(new RuntimeException("SNAPSHOT Request"))
      else
        Success(Nil)
    case MavenFormat(groupIds, _, artifactId, _, scalaVersion, _, sbtVersion, version, fileName, _, classifier, ext) =>
      if (version.equalsIgnoreCase("unspecified")) {
        Failure(new RuntimeException("Gradle Version-Unspecified Request"))
      } else if (version.toUpperCase.endsWith("SNAPSHOT")) {
        Failure(new RuntimeException("SNAPSHOT Request"))
      } else {
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
      }
    case IvyFormat(organization, module, _, scalaVersion, _, sbtVersion, revision, typ, fileName, artifact, _, classifier, ext)
    =>
      val result = if (scalaVersion == null && sbtVersion == null) {
        for (scala <- supportedScalaVersion; sbt <- supportedSbtVersion) yield
          s"/${organization.split("\\.").mkString("/")}/${module}_${scala}_$sbt/$revision/$module-$revision.$ext"
      } else Nil
      if (revision.toUpperCase.endsWith("SNAPSHOT")) {
        Failure(new RuntimeException("SNAPSHOT Request"))
      } else {
        Success(result)
      }
    case _ =>
      Failure(new RuntimeException("Invalid Request"))
  }

  val resourceHandlers: Agent[Map[FileResourceManager, ResourceHandler]] = Agent(null)

  def handle(exchange: HttpServerExchange): Unit = {
    val uri = exchange.getRequestURI
    val method = exchange.getRequestMethod
    uri match {
      case "/" =>
        redirectTo(exchange, "/admin/index.html")
      case "/favicon.ico" =>
        redirectTo(exchange, "/admin/favicon.ico")
      case _ =>
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
  }

  def init(): Unit = {
    configPersister ! 'LoadConfig // this does nothing but eagerly init Repox
  }
}
