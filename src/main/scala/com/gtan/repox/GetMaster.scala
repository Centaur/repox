package com.gtan.repox

import java.net.URL
import java.nio.file.{Files, StandardCopyOption}

import akka.actor._
import com.gtan.repox.GetWorker.{Cleanup, PeerChosen, WorkerDead}
import com.gtan.repox.Head404Cache.Query
import com.ning.http.client.FluentCaseInsensitiveStringsMap
import com.typesafe.scalalogging.LazyLogging
import collection.JavaConverters._

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

class GetMaster(val uri: String, val from: Vector[Repo]) extends Actor with ActorLogging {

  import scala.concurrent.duration._

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 3, withinTimeRange = 1 minute)(super.supervisorStrategy.decider)


  private def reduce(xss: Seq[Seq[Repo]], notFoundIn: Set[Repo]): Seq[Seq[Repo]] = {
    xss.map(_.filterNot(notFoundIn.contains)).filter(_.nonEmpty)
  }

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
      candidateRepos = reduce(candidateRepos, repos)
      if (candidateRepos.isEmpty) {
        context.parent ! GetQueueWorker.Get404(uri)
        self ! PoisonPill
      } else {

        log.debug(s"Try ${candidateRepos.map(_.map(_.name))} $uri")
        thisLevel = candidateRepos.head
        children = for (upstream <- thisLevel) yield {
          val upstreamUrl = upstream.base + uri
          val upstreamHost = new URL(upstreamUrl).getHost
          val requestHeaders = new FluentCaseInsensitiveStringsMap()
          requestHeaders.put("Host", List(upstreamHost).asJava)
          requestHeaders.put("Accept-Encoding", List("identity").asJava)
          val childActorName = s"${upstream.name}_${Random.nextInt()}"
          context.actorOf(
            Props(classOf[GetWorker], upstream, uri, requestHeaders),
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
              log.debug(s"all child failed. to next level.")
              candidateRepos = tail
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
            log.info(s"all child failed. to next level.")
            candidateRepos = tail
            reset()
        }
      }
    case msg@GetWorker.Completed(path, repo) =>
      if (sender == chosen) {
        resolvedPath.getParent.toFile.mkdirs()
        Files.move(path, resolvedPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        log.info(s"GetWorker ${sender().path.name} completed $uri.")
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
    case WorkerDead =>
      if (sender != chosen)
        sender ! Cleanup
      else throw new Exception("Chosen worker dead. Restart and rechoose.")
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
