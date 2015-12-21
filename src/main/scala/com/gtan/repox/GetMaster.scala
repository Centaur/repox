package com.gtan.repox

import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.file.Path
import java.nio.file.StandardCopyOption._

import akka.actor._
import com.google.common.base.Charsets
import com.google.common.hash.Hashing
import com.gtan.repox.GetWorker.{Cleanup, PeerChosen}
import com.gtan.repox.Head404Cache.{NotFound, Query}
import com.gtan.repox.data.Repo
import com.typesafe.scalalogging.LazyLogging

import scala.language.postfixOps

object GetMaster extends LazyLogging {

  import scala.concurrent.duration._

  implicit val timeout = new akka.util.Timeout(1 seconds)
}

/**
 * 负责某一个 uri 的 Get, 可能由多个 upstream Repo 生成多个 GetWorker
  *
  * @param uri 要获取的 uri
 * @param from 可能的 Repo
 */
class GetMaster(val uri: String, val from: Seq[Repo]) extends Actor with ActorLogging {
  import scala.concurrent.duration._

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 3, withinTimeRange = 1 minute)(super.supervisorStrategy.decider)

  private[this] def excludeNotFound[T](xss: Seq[Seq[T]], notFoundIn: Set[T]): Seq[Seq[T]] =
    xss.map(_.filterNot(notFoundIn)).filter(_.nonEmpty)

  val (resolvedPath, resolvedChecksumPath) = Repox.resolveToPaths(uri)
  var downloadedTempFilePath: Path = _

  var workerChosen = false
  var chosenWorker: ActorRef = null
  var chosenRepo: Repo = null
  var childFailCount = 0
  var candidateRepos = Repox.orderByPriority(
                                              if (Repox.isIvyUri(uri))
                                                from.filterNot(_.maven)
                                              else from
                                            )

  var children: Seq[ActorRef] = _

  private[this] def waitFor404Cache: Receive = {
    case Head404Cache.ExcludeRepos(repos) =>
      candidateRepos = excludeNotFound(candidateRepos, repos)
      if (candidateRepos.isEmpty) {
        context.parent ! GetQueueWorker.Get404(uri)
        self ! PoisonPill
      } else {
        log.debug(s"Try ${candidateRepos.map(_.map(_.name))} $uri")
        val thisLevel = candidateRepos.head
        children = for (upstream <- thisLevel) yield {
          startAWorker(upstream, uri)
        }
        context become gettingFile
      }
  }

  private[this] def startAWorker(upstream: Repo, target: String): ActorRef = {
    context.actorOf(
      Props(classOf[GetWorker], upstream, target, None, -1L),
      name = s"GetWorker_${URLEncoder.encode(upstream.name, "UTF-8")}_${Repox.nextId}"
    )
  }

  private[this] def askHead404Cache(): Unit = {
    Repox.head404Cache ! Query(uri)
  }

  askHead404Cache()

  def receive = waitFor404Cache

  def gettingFile: Receive = {
    case GetWorker.Resume(repo, tempFilePath, totalLength) =>
      chosenWorker = context.actorOf(
        Props(classOf[GetWorker], repo, uri, Some(tempFilePath), totalLength),
        name = s"GetWorker_${repo.name}_${Repox.nextId}"
      )
      children = chosenWorker :: Nil
    case GetWorker.Failed(t) =>
      if (chosenWorker == sender()) {
        log.debug(s"Chosen worker dead. Rechoose")
        reset()
      } else {
        childFailCount += 1
        if (childFailCount == children.length) {
          candidateRepos.toList match {
            case Nil =>
              log.debug(s"GetMaster all child failed. 404")
              context.parent ! GetQueueWorker.Get404(uri)
              self ! PoisonPill
            case head :: tail =>
              if (from.length > 1) {
                log.debug(s"all child failed. to next level.")
                candidateRepos = tail
              } else {
                log.debug(s"only one repo, no other choose, retry. $uri")
              }
              reset()
          }
        }
      }
    case GetWorker.UnsuccessResponseStatus(status) =>
      childFailCount += 1
      if (childFailCount == children.length) {
        candidateRepos.toList match {
          case Nil =>
            log.info(s"GetMaster all child failed. 404")
            context.parent ! GetQueueWorker.Get404(uri)
            self ! PoisonPill
          case head :: tail =>
            if (from.length > 1) {
              log.debug(s"all child failed. to next level.")
              candidateRepos = tail
            } else {
              log.debug(s"only one repo, no other choose, retry. $uri")
            }
            reset()
        }
      }
    case GetWorker.Completed(path, repo) =>
      if (sender() == chosenWorker) {
        for(child <- children) {
          child ! PoisonPill
        }
        if (!uri.endsWith(".sha1")) {
          downloadedTempFilePath = path
          chosenRepo = repo
          chosenWorker = startAWorker(repo, uri + ".sha1")
          children = chosenWorker :: Nil
          context become gettingChecksum
        } else {
          log.debug(s"A standalone .sha1 request, stop trying to retrieve its checksum.")
          resolvedPath.getParent.toFile.mkdirs()
          java.nio.file.Files.move(path, resolvedPath, REPLACE_EXISTING, ATOMIC_MOVE)
          context.parent ! GetQueueWorker.Completed(path, repo, checksumSuccess = true)
          self ! PoisonPill
        }
      } else {
        sender ! Cleanup
      }
    case GetWorker.HeadersGot(headers) =>
      if(children.contains(sender())) {
        if (!workerChosen) {
          log.debug(s"chose ${sender().path.name}, canceling others. ")
          for (others <- children.filterNot(_ == sender())) {
            others ! PeerChosen(sender())
          }
          chosenWorker = sender()
          workerChosen = true
        } else if (sender != chosenWorker) {
          sender ! PeerChosen(chosenWorker)
        }
      } else {
        log.debug(s"HeadersGot msg from previous level ${sender().path.name} received. Ignore.")
      }
  }

  def gettingChecksum: Receive = {
    case GetWorker.Completed(path, repo) =>
      val computed = com.google.common.io.Files.hash(downloadedTempFilePath.toFile, Hashing.sha1()).toString
      val downloaded = scala.io.Source.fromFile(path.toFile).mkString
      val checksumSuccess: Boolean = computed == downloaded
      if(checksumSuccess || candidateRepos.flatten.size == 1) {
        resolvedPath.getParent.toFile.mkdirs()
        log.info(s"GetWorker ${sender().path.name} completed $uri. Checksum ${if(checksumSuccess)  "success" else "failed"}")
        java.nio.file.Files.move(downloadedTempFilePath, resolvedPath, REPLACE_EXISTING, ATOMIC_MOVE)
        java.nio.file.Files.move(path, resolvedChecksumPath, REPLACE_EXISTING, ATOMIC_MOVE)
        context.parent ! GetQueueWorker.Completed(path, repo, checksumSuccess)
        children.foreach(child => child ! Cleanup)
        self ! PoisonPill
      } else {
        Repox.head404Cache ! NotFound(uri, repo)
        Repox.head404Cache ! NotFound(uri + ".sha1", repo)
        downloadedTempFilePath.toFile.delete()
        path.toFile.delete()
        reset()
      }
    case GetWorker.Failed(t) =>
      log.debug(s"GetWorker failed in gettingChecksum state: ${t.getMessage}. Restart.")
      sender ! PoisonPill
      chosenWorker = startAWorker(chosenRepo, uri + ".sha1")
      children = chosenWorker :: Nil
    case GetWorker.UnsuccessResponseStatus(status) =>
      log.debug(s"GetWorker get UnsuccessResponseStatus in gettingChecksum state: $status. Restart.")
      sender ! PoisonPill
      chosenWorker = startAWorker(chosenRepo, uri + ".sha1")
      children = chosenWorker :: Nil
    case msg =>
      log.debug(s"Received message in gettingChecksum state: $msg. Ignore.")
  }


  private[this] def reset() = {
    childFailCount = 0
    chosenWorker = null
    workerChosen = false
    for (child <- children) child ! PoisonPill
    askHead404Cache()
    context become waitFor404Cache
  }
}
