package com.gtan.repox

import akka.actor.{Actor, ActorLogging}
import com.gtan.repox.HeaderCache.{Found, NotFound, Query}

/**
 * Created by IntelliJ IDEA.
 * User: xf
 * Date: 14/11/24
 * Time: 下午10:55
 */
object HeaderCache {

  case class Query(uri: String)

  case class Found(uri: String, repo: Repo)

  case class NotFound(uri: String, repo: Repo)

  case class Asking(uri: String)

  import scala.concurrent.duration._

  val idleTimeout = 7 days
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
        case found@Some(Entry(repos, timestamp)) =>
          if (expired(timestamp)) {
            sender ! None
            data = data - uri
          } else {
            sender ! found
            data = data.updated(uri, Entry(repos, Timestamp.now))
          }
      }
    case Found(uri, repo) =>
      data.get(uri) match {
        case None =>
          data = data.updated(uri, Entry(Set(repo), Timestamp.now))
        case Some(Entry(repos, timestamp)) =>
          if (expired(timestamp)) {
            data.updated(uri, Entry(Set(repo), Timestamp.now))
          } else {
            data = data.updated(uri, Entry(repos + repo, Timestamp.now))
          }
      }
    case NotFound(uri, repo) =>
      data.get(uri) match {
        case None =>
          data = data.updated(uri, Entry(Set.empty[Repo], Timestamp.now))
        case Some(Entry(repos, timestamp)) =>
          if (expired(timestamp)) {
            data = data.updated(uri, Entry(Set.empty[Repo], Timestamp.now))
          } else {
            data = data.updated(uri, Entry(repos - repo, Timestamp.now))
          }
      }
  }
}
