package com.gtan.repox

import akka.actor.Actor.Receive
import akka.actor._
import com.gtan.repox.Head404Cache.Query
import com.gtan.repox.Repox.ResponseHeaders
import com.ning.http.client.FluentCaseInsensitiveStringsMap
import io.undertow.server.HttpServerExchange
import io.undertow.util.{Headers, StatusCodes}
import collection.JavaConverters._
import scala.collection.Set
import scala.util.Random

/**
 * Created by xf on 14/11/26.
 */

object HeadMaster {

  trait HeadResult

  case class FoundIn(repo: Repo, headers: ResponseHeaders) extends HeadResult

  case class NotFound(repo: Repo) extends HeadResult

  case class HeadTimeout(repo: Repo) extends HeadResult

}

class HeadMaster(val exchange: HttpServerExchange) extends Actor with ActorLogging {

  import com.gtan.repox.HeadMaster._

  val uri = exchange.getRequestURI

  val requestHeaders = new FluentCaseInsensitiveStringsMap()
  for (name <- exchange.getRequestHeaders.getHeaderNames.asScala) {
    requestHeaders.add(name.toString, exchange.getRequestHeaders.get(name))
  }
  requestHeaders.put(Headers.ACCEPT_ENCODING_STRING, List("identity").asJava)


  var children: List[ActorRef] = _

  var resultMap = Map.empty[ActorRef, HeadResult]
  var finishedChildren = 0
  var retryTimes = 0

  var candidateRepos: List[Repo] = _

  Repox.head404Cache ! Query(uri)

  def start: Receive = {
    case Head404Cache.ExcludeRepos(repos) =>
      candidateRepos = Repox.upstreams.filterNot(repos.contains)
      log.debug(s"CandidateRepos: ${candidateRepos.map(_.name)}")
      if (candidateRepos.isEmpty) {
        context.parent ! HeadQueueWorker.NotFound(exchange)
        self ! PoisonPill
      }
      children = for (upstream <- candidateRepos) yield {
        val childName = s"HeadWorker_${upstream.name}_${Random.nextInt()}"
        context.actorOf(
          Props(classOf[HeadWorker], upstream, uri, requestHeaders),
          name = childName)
      }
      context become working
  }

  override def receive = start

  def working: Receive = {
    case msg@FoundIn(repo, headers) =>
      finishedChildren += 1
      context.parent ! HeadQueueWorker.FoundIn(repo, headers, exchange)
      self ! PoisonPill
    case msg@NotFound(repo) =>
      finishedChildren += 1
      allReturned()
    case msg@HeadTimeout(repo) =>
      finishedChildren += 1
      allReturned()
  }

  def allReturned(): Unit = {
    if (finishedChildren == children.size) {
      // all returned
      retryTimes += 1
      log.debug(s"retryTimes = $retryTimes")
      if (retryTimes == 3) {
        context.parent ! HeadQueueWorker.NotFound(exchange)
        log.debug(s"retried 3 times, give up.")
        self ! PoisonPill
      } else {
        log.debug("All headworkers return 404. retry.")
        Repox.head404Cache ! Query(uri)
        finishedChildren = 0
        context become start
      }
    }

  }
}
