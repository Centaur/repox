package com.gtan.repox

import java.io.File
import java.nio.file.{Files, StandardCopyOption}

import akka.actor._
import com.gtan.repox.GetWorker.{Cleanup, PeerChosen}
import com.gtan.repox.Head404Cache.Query
import com.gtan.repox.data.Repo
import com.typesafe.scalalogging.LazyLogging

import scala.language.postfixOps
import scala.util.Random

/**
 * Created by IntelliJ IDEA.
 * User: xf
 * Date: 14/11/21
 * Time: 下午10:01
 */

object GetMaster extends LazyLogging {

  import scala.concurrent.duration._

  implicit val timeout = new akka.util.Timeout(1 seconds)
}

/**
 * 负责某一个 uri 的 Get, 可能由多个 upstream Repo 生成多个 GetWorker
 * @param uri 要获取的 uri
 * @param from 可能的 Repo
 */
class GetMaster(val uri: String, val from: Seq[Repo]) extends Actor with ActorLogging {

  import scala.concurrent.duration._

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 3, withinTimeRange = 1 minute)(super.supervisorStrategy.decider)


  private def excludeNotFound(xss: Seq[Seq[Repo]], notFoundIn: Set[Repo]): Seq[Seq[Repo]] = for {
    repos <- xss
    repo <- repos if !notFoundIn.contains(repo)
  } yield Seq(repo)


  val resolvedPath = Repox.resolveToPath(uri)
  var getterChosen = false
  var chosen: ActorRef = null
  var childFailCount = 0
  var candidateRepos = Repox.orderByPriority(
                                              if (Repox.isIvyUri(uri))
                                                from.filterNot(_.maven)
                                              else from
                                            )

  var thisLevel: Seq[Repo] = _
  var children: Seq[ActorRef] = _

  def waitFor404Cache: Receive = {
    case Head404Cache.ExcludeRepos(repos) =>
      candidateRepos = excludeNotFound(candidateRepos, repos)
      if (candidateRepos.isEmpty) {
        context.parent ! GetQueueWorker.Get404(uri)
        self ! PoisonPill
      } else {
        log.debug(s"Try ${candidateRepos.map(_.map(_.name))} $uri")
        thisLevel = candidateRepos.head
        children = for (upstream <- thisLevel) yield {
          val childActorName = s"${upstream.name}_${Random.nextInt()}"
          context.actorOf(
                           Props(classOf[GetWorker], upstream, uri, None, -1L),
                           name = s"GetWorker_$childActorName"
                         )
        }
        context become working
      }
  }

  def askHead404Cache(): Unit = {
    Repox.head404Cache ! Query(uri)
  }

  askHead404Cache()

  def receive = waitFor404Cache

  def working: Receive = {
    case GetWorker.Resume(repo, tempFilePath, totalLength) =>
      val childActorName = s"${repo.name}_${Random.nextInt()}"
      chosen = context.actorOf(
        Props(classOf[GetWorker], repo, uri, Some(tempFilePath), totalLength),
        name = s"GetWorker_$childActorName"
      )
      children = chosen :: Nil
    case GetWorker.Failed(t) =>
      if (chosen == sender()) {
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
      if (sender == chosen) {
        resolvedPath.getParent.toFile.mkdirs()
        log.info(s"GetWorker ${sender().path.name} completed $uri. ${path.toString} Filesize ${path.toFile.length()}")
        Files.move(path, resolvedPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        context.parent ! GetQueueWorker.Completed(path, repo)
        children.foreach(child => child ! Cleanup)
        self ! PoisonPill
      } else {
        sender ! Cleanup
      }
    case GetWorker.HeadersGot(headers) =>
      if (!getterChosen) {
        log.debug(s"chose ${sender().path.name}, canceling others. ")
        for (others <- children.filterNot(_ == sender())) {
          others ! PeerChosen(sender())
        }
        chosen = sender()
        getterChosen = true
      } else if (sender != chosen) {
        sender ! PeerChosen(chosen)
      }
  }


  private def reset() = {
    childFailCount = 0
    chosen = null
    getterChosen = false
    for (child <- children) child ! PoisonPill
    askHead404Cache()
    context become waitFor404Cache
  }
}
