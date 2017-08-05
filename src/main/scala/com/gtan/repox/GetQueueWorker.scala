package com.gtan.repox

import java.nio.file.Path

import akka.actor._
import com.gtan.repox.ExpirationManager.CreateExpiration
import com.gtan.repox.RequestQueueMaster.KillMe
import com.gtan.repox.config.Config
import com.gtan.repox.data.Repo
import io.undertow.server.HttpServerExchange

import scala.concurrent.duration._
import scala.language.postfixOps

object GetQueueWorker {

  case class Get404(uri: String)

  case class Completed(path: Path, repo: Repo, checksumSuccess: Boolean)

}

class GetQueueWorker(val uri: String) extends Actor with Stash with ActorLogging {

  import GetQueueWorker._

  override def receive = start

  def start: Receive = {
    case msg@Requests.Get(exchange) =>
      Repox.downloaded(uri) match {
        case Some(Tuple2(resourceManager, resourceHandler)) =>
          log.info(s"$uri downloaded. Serve immediately.")
          Repox.immediateFile(resourceHandler, exchange)
          suicide()
        case None =>
          log.info(s"$uri not downloaded. Downloading.")
          context.actorOf(Props(classOf[GetMaster], uri, Config.enabledRepos), s"GetMaster_${Repox.nextId}")
          self ! msg
          context become working
      }
    case msg@Requests.Get4s(request) =>
      Repox.downloaded4s(uri) match {
        case Some(file) =>
          log.info(s"$uri downloaded. Serve immediately.")
          Repox.immediateFile4s(sender, request, file)
          suicide()
        case None =>
          log.info(s"$uri not downloaded. Downloading.")
          context.actorOf(Props(classOf[GetMaster], uri, Config.enabledRepos), s"GetMaster_${Repox.nextId}")
          self ! msg
          context become working
      }
  }


  var found = false

  var deleteFileAfterResponse = false

  def working: Receive = {
    case Requests.Get(_) => stash()
    case Requests.Get4s(_) => stash()
    case result@Completed(path, repo, checksumSuccess) =>
      log.debug(s"GetQueueWorker completed $uri")
      found = true
      deleteFileAfterResponse = !checksumSuccess
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
      if (found) {
        log.debug(s"flushWaiting $exchange 200. Sending file $uri")
        Repox.sendFile(Repox.resourceHandlers.get().apply(Repox.storageManager), exchange)
        if (deleteFileAfterResponse) {
          context.actorOf(Props(classOf[FileDeleter], uri, 'GetQueueWorker))
        } else Config.enabledExpireRules.find(rule => uri.matches(rule.pattern)).foreach { r =>
          Repox.expirationPersister ! CreateExpiration(uri, r.duration)
        }
      } else {
        log.debug(s"flushWaiting $exchange 404")
        Repox.respond404(exchange)
      }
    case Requests.Get4s(request) =>
      import org.http4s.dsl.{uri => _, _}
      if (found) {
        log.debug(s"flushWaiting $request 200. Sending file $uri")
        Repox.sendFile4s(sender(), request, Config.storagePath)
        if (deleteFileAfterResponse) {
          context.actorOf(Props(classOf[FileDeleter], uri, 'GetQueueWorker))
        } else Config.enabledExpireRules.find(rule => uri.matches(rule.pattern)).foreach { r =>
          Repox.expirationPersister ! CreateExpiration(uri, r.duration)
        }
      } else {
        log.debug(s"flushWaiting $request 404")
        sender ! NotFound()
      }

    case ReceiveTimeout =>
      suicide()
  }

  private def suicide(): Unit = {
    context.parent ! KillMe(Queue('get, uri))
  }

}
