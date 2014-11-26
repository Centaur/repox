package com.gtan.repox

import akka.actor.{Actor, ActorLogging}
import com.gtan.repox.HeaderCache.{NotFound, Query}

import scala.language.postfixOps

/**
 * repo that does not have some uri according to previous HEAD request
 * hold for 1 day
 * User: xf
 * Date: 14/11/24
 * Time: 下午10:55
 */
object HeaderCache {

  case class Query(uri: String)

  case class NotFound(uri: String, repo: Repo)

  import scala.concurrent.duration._

  val idleTimeout = 1 days
}

case class Entry(repos: Set[Repo], timestamp: Timestamp)

class HeaderCache extends Actor with ActorLogging {
  var data = Map.empty[String, Entry]

  def expired(timestamp: Timestamp): Boolean =
    (timestamp + HeaderCache.idleTimeout).isPast

  override def receive = {
    case Query(uri) =>
      data.get(uri) match {
        case None => sender ! None
        case entry@Some(Entry(repos, timestamp)) =>
          if (expired(timestamp)) {
            data = data - uri
            sender ! None
          } else {
            sender ! entry
          }
      }
    case NotFound(uri, repo) =>
      data.get(uri) match {
        case None =>
          data = data.updated(uri, Entry(Set(repo), Timestamp.now))
        case Some(Entry(repos, timestamp)) =>
          if (expired(timestamp)) {
            data = data.updated(uri, Entry(Set(repo), Timestamp.now))
          } else {
            data = data.updated(uri, Entry(repos + repo, Timestamp.now))
          }
      }
  }
}
