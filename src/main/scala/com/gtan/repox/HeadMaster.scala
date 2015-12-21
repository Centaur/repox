package com.gtan.repox

import java.net.URLEncoder

import akka.actor.Actor.Receive
import akka.actor._
import com.gtan.repox.Head404Cache.Query
import com.gtan.repox.Repox.ResponseHeaders
import com.gtan.repox.config.Config
import com.gtan.repox.data.Repo
import com.ning.http.client.FluentCaseInsensitiveStringsMap
import io.undertow.server.HttpServerExchange
import io.undertow.util.{Headers, StatusCodes}
import collection.JavaConverters._
import scala.collection.Set
import scala.util.Random

object HeadMaster {

  case class FoundIn(repo: Repo, headers: ResponseHeaders)

  case class NotFound(repo: Repo)

  case class HeadTimeout(repo: Repo)

}

class HeadMaster(val exchange: HttpServerExchange) extends Actor with ActorLogging {

  import com.gtan.repox.HeadMaster._

  val uri = exchange.getRequestURI
  val upstreams = {
    val candidates = Config.enabledRepos.filterNot(_.getOnly)
    if(Repox.isIvyUri(uri))
      candidates.filterNot(_.maven)
    else candidates
  }

  val requestHeaders = new FluentCaseInsensitiveStringsMap()
  for (name <- exchange.getRequestHeaders.getHeaderNames.asScala) {
    requestHeaders.add(name.toString, exchange.getRequestHeaders.get(name))
  }
  requestHeaders.put(Headers.ACCEPT_ENCODING_STRING, List("identity").asJava)


  var children: Seq[ActorRef] = _

  var finishedChildren = 0
  var retryTimes = 0

  var candidateRepos: Seq[Repo] = _

  Repox.head404Cache ! Query(uri)

  def start: Receive = {
    case Head404Cache.ExcludeRepos(repos) =>
      candidateRepos = upstreams.filterNot(repos.contains)
      log.debug(s"CandidateRepos: ${candidateRepos.map(_.name)}")
      if (candidateRepos.isEmpty) {
        context.parent ! HeadQueueWorker.NotFound(exchange)
        self ! PoisonPill
      }
      children = for (upstream <- candidateRepos) yield {
        val childName = s"HeadWorker_${URLEncoder.encode(upstream.name, "UTF-8")}_${Repox.nextId}"
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
      testAllReturned()
    case msg@HeadTimeout(repo) =>
      finishedChildren += 1
      testAllReturned()
  }

  def testAllReturned(): Unit = {
    if (finishedChildren == children.size) {
      // all returned
      retryTimes += 1
      if (retryTimes == Config.headRetryTimes) {
        context.parent ! HeadQueueWorker.NotFound(exchange)
        log.debug(s"retried ${Config.headRetryTimes} times, give up.")
        self ! PoisonPill
      } else {
        log.debug("All headworkers return 404. retry.")
        finishedChildren = 0
        Repox.head404Cache ! Query(uri)
        context become start
      }
    }
  }
}
