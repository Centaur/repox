package com.gtan.repox.data

import java.net.URL
import java.util.concurrent.atomic.AtomicLong

import com.gtan.repox.config.Config
import play.api.libs.json.Json

/**
 * Created by IntelliJ IDEA.
 * User: xf
 * Date: 14/11/23
 * Time: 下午3:55
 */
case class Repo(id: Option[Long], name: String, base: String, priority: Int, getOnly: Boolean = false, maven: Boolean = false, disabled: Boolean = false) {
  def absolute(uri: String): String = base + uri
  lazy val host = new URL(base).getHost
}

object Repo {
  lazy val nextId: AtomicLong = new AtomicLong(Config.repos.flatMap(_.id).max)

  implicit val format = Json.format[Repo]
}

