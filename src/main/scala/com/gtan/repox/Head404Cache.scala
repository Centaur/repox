package com.gtan.repox

import akka.actor.{Actor, ActorLogging}
import com.gtan.repox.Head404Cache.{NotFound, Query}
import com.gtan.repox.data.Repo

import scala.language.postfixOps

/**
 * repo that does not have some uri according to previous HEAD request
 * hold for 1 day
 * User: xf
 * Date: 14/11/24
 * Time: 下午10:55
 */
object Head404Cache {

  case class Query(uri: String) // answer Set[Repo]

  case class NotFound(uri: String, repo: Repo)

  case class ExcludeRepos(repos: Set[Repo])

  import scala.concurrent.duration._

  val idleTimeout = 1 days
}

class Head404Cache extends Actor with ActorLogging {
  import Head404Cache._

  var data = Map.empty[String, Map[Repo, Timestamp]]

  def expired(timestamp: Timestamp): Boolean =
    (timestamp + Head404Cache.idleTimeout).isPast

  override def receive = {
    case Query(uri) =>
      data.get(uri) match {
        case None => sender ! ExcludeRepos(Set.empty[Repo])
        case Some(map) =>
          val notFoundAndNotExpired = map.filter { case (_, timestamp) => !expired(timestamp)}
          if(notFoundAndNotExpired.isEmpty){
            data = data - uri
          } else {
            data = data.updated(uri, notFoundAndNotExpired)
          }
          sender ! ExcludeRepos(notFoundAndNotExpired.keySet)
      }
    case NotFound(uri, repo) =>
      data.get(uri) match {
        case None =>
          data = data.updated(uri, Map(repo -> Timestamp.now))
        case Some(map) =>
          val notFoundAndNotExpired = map.filter { case (_, timestamp) => !expired(timestamp)}
          data = data.updated(uri, notFoundAndNotExpired + (repo -> Timestamp.now))
      }
  }
}
