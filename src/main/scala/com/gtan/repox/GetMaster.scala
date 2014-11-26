package com.gtan.repox

import java.net.URL
import java.nio.file.{Files, Path, StandardCopyOption}

import akka.actor.SupervisorStrategy.{Escalate, Resume}
import akka.actor._
import com.gtan.repox.GetWorker.{WorkerDead, Cleanup, PeerChosen}
import com.gtan.repox.HeaderCache.Query
import com.gtan.repox.SourceCache.{WhichRepo, Clear, Source}
import com.ning.http.client.FluentCaseInsensitiveStringsMap
import com.typesafe.scalalogging.LazyLogging
import io.undertow.Handlers
import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.resource.FileResourceManager

import scala.collection.JavaConverters._
import scala.language.postfixOps
import scala.util.Random

/**
 * Created by IntelliJ IDEA.
 * User: xf
 * Date: 14/11/21
 * Time: 下午10:01
 */

import akka.pattern.ask

object GetMaster extends LazyLogging {
  import concurrent.duration._
  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val timeout = new akka.util.Timeout(5 seconds)
  def run(exchange: HttpServerExchange, resolvedPath: Path, candidates: List[List[Repo]]): Unit = {
    val actorName = s"Parent-${Random.nextInt()}"
    candidates match {
      case head :: tail =>
        if (resolvedPath.endsWith(".sha1")) {
          val file = exchange.getRequestURI.dropRight(5)
          (Repox.sourceCache ? WhichRepo(file)).onSuccess {
            case Source(uri, repo) =>
              logger.debug(s"$uri was downloaded from ${repo.name}, sha1 should be downloaded from this repo.")
              Repox.system.actorOf(Props(classOf[GetMaster], exchange, resolvedPath, List(repo), Nil), actorName)
          }
        } else
          Repox.system.actorOf(Props(classOf[GetMaster], exchange, resolvedPath, head, tail), s"Parent-${Random.nextInt()}")
      case Nil =>
        exchange.setResponseCode(404)
        exchange.endExchange()
    }
  }
}

class GetMaster(exchange: HttpServerExchange,
                resolvedPath: Path,
                thisLevel: List[Repo],
                nextLevel: List[List[Repo]]) extends Actor with ActorLogging {

  import scala.concurrent.duration._

  val requestHeaders = new FluentCaseInsensitiveStringsMap()
  for (name <- exchange.getRequestHeaders.getHeaderNames.asScala) {
    requestHeaders.add(name.toString, exchange.getRequestHeaders.get(name))
  }

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 3, withinTimeRange = 1 minute)(super.supervisorStrategy.decider)

  val children = for (upstream <- thisLevel) yield {
    val uri = exchange.getRequestURI
    val upstreamUrl = upstream.base + uri
    val upstreamHost = new URL(upstreamUrl).getHost
    requestHeaders.put("Host", List(upstreamHost).asJava)
    requestHeaders.put("Accept-Encoding", List("identity").asJava)

    val childActorName = upstream.name

    context.actorOf(
      Props(classOf[GetWorker], upstream, uri, requestHeaders),
      name = s"Getter_$childActorName"
    )
  }

  var getterChosen = false
  var chosen: ActorRef = null
  var child404Count = 0

  def receive = {
    case GetWorker.UnsuccessResponseStatus(status) =>
      child404Count += 1
      if (child404Count == children.length) {
        log.debug(s"all child failed. to next level.")
        GetMaster.run(exchange, resolvedPath, nextLevel)
        self ! PoisonPill
      }
    case GetWorker.Completed(path, repo) =>
      if (sender == chosen) {
        log.debug(s"getter ${sender().path.name} completed, saved to ${path.toAbsolutePath}")
        resolvedPath.getParent.toFile.mkdirs()
        Files.move(path, resolvedPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        Handlers.resource(new FileResourceManager(Repox.storage.toFile, 100 * 1024)).handleRequest(exchange)
        children.foreach(child => child ! Cleanup)
        if (!path.endsWith(".sha1")) {
          Repox.sourceCache ! Source(exchange.getRequestURI, repo)
        } else {
          Repox.sourceCache ! Clear(exchange.getRequestURI)
        }
        self ! PoisonPill
      } else {
        sender ! Cleanup
      }
    case GetWorker.HeadersGot(_) =>
      if (!getterChosen) {
        log.debug(s"chose ${sender().path.name}, canceling others.")
        for (others <- children.filterNot(_ == sender())) {
          others ! PeerChosen(sender())
        }
        chosen = sender()
        getterChosen = true
      } else if (sender != chosen) {
        sender ! PeerChosen(chosen)
      }
    case WorkerDead =>
      if (sender != chosen)
        sender ! Cleanup
      else throw new Exception("Chosen worker dead. Restart and rechoose.")
  }


}
