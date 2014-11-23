package com.gtan.repox

import java.net.URL
import java.nio.file.{Files, Path, StandardCopyOption}

import akka.actor._
import com.gtan.repox.GetWorker.{WorkerDead, Cleanup, PeerChosen}
import com.ning.http.client.FluentCaseInsensitiveStringsMap
import io.undertow.Handlers
import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.resource.FileResourceManager

import scala.collection.JavaConverters._
import scala.util.Random

/**
 * Created by IntelliJ IDEA.
 * User: xf
 * Date: 14/11/21
 * Time: 下午10:01
 */

object GetMaster {
  def run(exchange: HttpServerExchange, resolvedPath: Path, candidates: List[List[Repo]]): Unit = candidates match {
    case head :: tail =>
      val filtered =
        if(exchange.getRequestURI.endsWith("pom.sha1") || exchange.getRequestURI.endsWith(".pom"))
        head.filterNot(_.name == "typesafe")
        else head
      Repox.system.actorOf(Props(classOf[GetMaster], exchange, resolvedPath, filtered, tail), s"Parent-${Random.nextInt()}")
    case Nil =>
      exchange.setResponseCode(404)
      exchange.endExchange()
  }
}

class GetMaster(exchange: HttpServerExchange,
                resolvedPath: Path,
                thisLevel: List[Repo],
                nextLevel: List[List[Repo]]) extends Actor with ActorLogging {

  val requestHeaders = new FluentCaseInsensitiveStringsMap()
  for (name <- exchange.getRequestHeaders.getHeaderNames.asScala) {
    requestHeaders.add(name.toString, exchange.getRequestHeaders.get(name))
  }

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
      if(child404Count == children.length) {
        log.debug(s"all child failed. to next level.")
        GetMaster.run(exchange, resolvedPath, nextLevel)
        self ! PoisonPill
      }
    case GetWorker.Completed(path) =>
      if (sender == chosen) {
        log.debug(s"getter $sender completed, saved to ${path.toAbsolutePath}")
        resolvedPath.getParent.toFile.mkdirs()
        Files.move(path, resolvedPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        Handlers.resource(new FileResourceManager(Repox.storage.toFile, 100 * 1024)).handleRequest(exchange)
        children.foreach(child => child ! Cleanup)
        self ! PoisonPill
      } else {
        sender ! Cleanup
      }
    case GetWorker.HeadersGot(_) =>
      if (!getterChosen) {
        log.debug(s"chose $sender, canceling others.")
        for (others <- children.filterNot(_ == sender())) {
          others ! PeerChosen(sender)
        }
        chosen = sender
        getterChosen = true
      } else if (sender != chosen) {
        sender ! PeerChosen(chosen)
      }
    case WorkerDead =>
      if (sender == chosen)
        throw new Exception("Chosen worker dead. Restart and rechoose.")
      else
        sender ! Cleanup
  }


}
