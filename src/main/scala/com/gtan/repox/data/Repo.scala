package com.gtan.repox.data

import com.gtan.repox.Repox
import com.gtan.repox.config.Config
import play.api.libs.json.Json

/**
 * Created by IntelliJ IDEA.
 * User: xf
 * Date: 14/11/23
 * Time: 下午3:55
 */
case class Repo(id: Option[Long], name: String, base: String, priority: Int, getOnly: Boolean = false, maven: Boolean = false, disabled: Boolean = false)

object Repo {
  def nextId: Long = Config.repos.flatMap(_.id).max + 1

  implicit val format = Json.format[Repo]
}

