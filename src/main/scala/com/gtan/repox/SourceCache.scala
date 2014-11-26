package com.gtan.repox

import akka.actor.{Actor, ActorLogging}

/**
 * Created by IntelliJ IDEA.
 * User: xf
 * Date: 14/11/26
 * Time: 下午11:22
 */
object SourceCache {
  case class Source(uri: String, repo: Repo)
  case class WhichRepo(uri: String)
  case class Clear(uri: String)
}
class SourceCache extends Actor with ActorLogging{
  import SourceCache._

  var data = Map.empty[String, Repo]

  override def receive = {
    case Source(uri, repo) =>
      log.debug(s"$uri downloaded from ${repo.name}")
      data = data.updated(uri, repo)
    case WhichRepo(uri) =>
      log.debug(s"$uri was downloaded from ${data(uri)}")
      sender ! Source(uri, data(uri))
    case Clear(uri) =>
      data = data - uri
  }
}
