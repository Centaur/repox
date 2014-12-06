package com.gtan.repox

import com.gtan.repox.config.Config
import collection.JavaConverters._

/**
 * Created by IntelliJ IDEA.
 * User: xf
 * Date: 14/11/23
 * Time: 下午12:15
 */
case class Immediate404Rule(id: Long, include: String, exclude: Option[String] = None) {
  def matches(uri: String): Boolean = {
    val included = uri.matches(include)
    exclude match {
      case None => included
      case Some(regex) =>
        val excluded = uri.matches(regex)
        included && !excluded
    }
  }

  def toMap: java.util.Map[String, Any] = {
    val withoutId = Map(
      "include" -> include
    )
    val withId = if (id == -1) {
      withoutId.updated("id", id)
    } else withoutId
    val withExclude = exclude.fold(withId)(ex => withId.updated("exclude", ex))
    withExclude.asJava
  }
}

object Immediate404Rule {
  def nextId: Long = Config.repos.map(_.id).max + 1

  def fromJson(json: String): Immediate404Rule = {
    val map = Repox.gson.fromJson(json, classOf[java.util.Map[String, String]]).asScala
    val withoutId = Immediate404Rule(
      id = -1,
      include = map("include"),
      exclude = map.get("exclude")
    )
    map.get("id").fold(withoutId) { idStr =>
      withoutId.copy(id = idStr.toLong)
    }
  }
}

case class BlacklistRule(pattern: String, repoName: String)
