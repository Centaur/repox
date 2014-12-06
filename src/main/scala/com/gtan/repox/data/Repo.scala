package com.gtan.repox.data

import com.gtan.repox.Repox
import com.gtan.repox.config.Config

/**
 * Created by IntelliJ IDEA.
 * User: xf
 * Date: 14/11/23
 * Time: 下午3:55
 */
case class Repo(id: Option[Long], name: String, base: String, priority: Int, getOnly: Boolean = false, maven: Boolean = false, disabled: Boolean = false) {
  def toMap: Map[String, Any] = {
    val withoutId = Map("name" -> name, "base" -> base, "priority" -> priority, "getOnly" -> getOnly, "maven" -> maven, "disabled" -> disabled)
    id.fold(withoutId) { _id =>
      withoutId.updated("id", _id)
    }
  }

}

object Repo {
  def nextId: Long = Config.repos.flatMap(_.id).max + 1

  def apply(map: java.util.Map[String, String]): Repo = this.apply(
    id = if (map.containsKey("id")) Some(map.get("id").toLong) else None,
    name = map.get("name"),
    base = map.get("base"),
    priority = map.get("priority").toInt,
    getOnly = map.get("getOnly").toBoolean,
    maven = map.get("maven").toBoolean
  )

  def fromJson(json: String): Repo = {
    val map = Repox.gson.fromJson(json, classOf[java.util.Map[String, String]])
    Repo(map)
  }
}

