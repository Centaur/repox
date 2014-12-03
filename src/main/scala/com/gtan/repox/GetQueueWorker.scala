package com.gtan.repox

import java.nio.file.Path

import akka.actor._
import com.gtan.repox.SHA1Checker.Check
import com.gtan.repox.config.Config
import io.undertow.server.HttpServerExchange

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random
import scala.util.Random

object GetQueueWorker {
  case class Get404(uri: String)
  case class Completed(path: Path, repo: Repo)
}
class GetQueueWorker(val uri: String) extends Actor with Stash with ActorLogging {
  import GetQueueWorker._

  override def receive = start

  def start: Receive = {
    case Requests.Download(u, from) =>
      assert(u == uri)
      if(!Repox.downloaded(uri)){
        log.info(s"$uri not downloaded. Downloading.")
        context.actorOf(Props(classOf[GetMaster], uri, from), s"DownloadMaster_${Random.nextInt()}")
      }
      context become working
    case msg@Requests.Get(exchange) =>
      assert(uri == exchange.getRequestURI)
      if (Repox.downloaded(uri)) {
        log.info(s"$uri downloaded. Serve immediately.")
        Repox.immediateFile(exchange)
        suicide()
      } else {
        log.info(s"$uri not downloaded. Downloading.")
        context.actorOf(Props(classOf[GetMaster], uri, Config.repos), s"GetMaster_${Random.nextInt()}")
        self ! msg
        context become working
      }
  }

  var found = false
  def working: Receive = {
    case Requests.Get(_) =>
      stash()
    case result @ Completed(path, repo) =>
      log.debug(s"GetQueueWorker completed $uri")
      found = true
      if(!uri.endsWith(".sha1")) {
        log.debug(s"Prefetch $uri.sha1")
        context.parent ! Requests.Download(uri + ".sha1", Seq(repo))
      } else {
        Repox.sha1Checker ! Check(uri.dropRight(5))
      }
      unstashAll()
      context.setReceiveTimeout(1 second)
      context become flushWaiting
    case Get404(u) =>
      log.debug(s"GetQueueWorker 404 $u")
      found = false
      unstashAll()
      context.setReceiveTimeout(1 second)
      context become flushWaiting
  }

  def flushWaiting: Receive = {
    case Requests.Get(exchange) =>
      if(found) {
        log.debug(s"flushWaiting $exchange 200")
        Repox.immediateFile(exchange)
      } else {
        log.debug(s"flushWaiting $exchange 404")
        Repox.respond404(exchange)
      }
    case ReceiveTimeout =>
      suicide()
  }

  private def suicide(): Unit ={
    context.parent ! RequestQueueMaster.Dead(Queue('get, uri))
    self ! PoisonPill
  }

}
