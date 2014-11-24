package com.gtan.repox

import akka.actor.{Actor, ActorLogging}
import com.gtan.repox.HeaderCache.{Asking, Found, Query}

/**
 * Created by IntelliJ IDEA.
 * User: xf
 * Date: 14/11/24
 * Time: 下午10:55
 */
object HeaderCache {

  case class Query(uri: String)

  case class Found(uri: String, repo: Repo)

  case class Asking(uri: String)

  import concurrent.duration._
  val timeout = 10 minutes
}

class HeaderCache extends Actor with ActorLogging {
  var data = Map.empty[String, Vector[Repo]]

  override def receive = {
    case Query(uri) =>
      sender ! data.get(uri)
    case Found(uri, repo) =>
      if (data.contains(uri)) {
        data = data.updated(uri, data(uri) :+ repo)
      } else {
        data = data.updated(uri, Vector(repo))
      }
    case Asking(uri) =>
      data = data.updated(uri, Vector.empty[Repo])
  }
}
