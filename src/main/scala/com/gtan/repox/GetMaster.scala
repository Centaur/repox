package com.gtan.repox

import java.net.URL
import java.nio.file.{Files, Path, StandardCopyOption}

import akka.actor.{ActorRef, ActorLogging, Actor, Props}
import com.gtan.repox.GetWorker.{Cleanup, PeerChosen}
import com.ning.http.client.FluentCaseInsensitiveStringsMap
import io.undertow.Handlers
import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.resource.FileResourceManager

import scala.collection.JavaConverters._

/**
 * Created by IntelliJ IDEA.
 * User: xf
 * Date: 14/11/21
 * Time: 下午10:01
 */
class GetMaster(exchange: HttpServerExchange, resolvedPath: Path) extends Actor with ActorLogging {

  val requestHeaders = new FluentCaseInsensitiveStringsMap()
  for (name <- exchange.getRequestHeaders.getHeaderNames.asScala) {
    requestHeaders.add(name.toString, exchange.getRequestHeaders.get(name))
  }

  val children = for (upstream <- Repox.upstreams) yield {
    val uri = exchange.getRequestURI
    val upstreamUrl = upstream + uri
    val upstreamHost = new URL(upstreamUrl).getHost
    requestHeaders.put("Host", List(upstreamHost).asJava)

    val childActorName = upstreamHost.split("\\.").mkString("_")
    log.debug(s"childActorName = $childActorName")

    context.actorOf(
      Props(classOf[GetWorker], upstream, uri, requestHeaders),
      name = s"Getter_$childActorName"
    )
  }

  var getterChosen = false
  var chosen : ActorRef = null

  def receive = {
    case GetWorker.Completed(path) =>
      log.debug(s"getter $sender completed, saved to ${path.toAbsolutePath}")
      resolvedPath.getParent.toFile.mkdirs()
      Files.move(path, resolvedPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
      Handlers.resource(new FileResourceManager(Repox.storage.toFile, 100 * 1024)).handleRequest(exchange)
      children.foreach(child => child ! Cleanup)
    case GetWorker.HeadersGot(_) =>
      if (!getterChosen) {
        log.debug(s"chose $sender, canceling others.")
        for (others <- children.filterNot(_ == sender())) {
          others ! PeerChosen(sender)
        }
        chosen = sender
        getterChosen = true
      } else {
        sender ! PeerChosen(chosen)
      }
  }


}
