package com.gtan.repox.data

import com.gtan.repox.Repox
import com.gtan.repox.config.Config

import scala.collection.JavaConverters._

/**
 * Created by IntelliJ IDEA.
 * User: xf
 * Date: 14/11/23
 * Time: 下午12:15
 */
case class Immediate404Rule(id: Option[Long], include: String, exclude: Option[String] = None, disabled: Boolean = false) {
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
    val withoutId:Map[String, Any] = Map(
      "include" -> include,
      "disable" -> disabled
    )
    val withId = id.fold(withoutId){ _id =>
      withoutId.updated("id", _id)
    }
    val withExclude = exclude.fold(withId)(ex => withId.updated("exclude", ex))
    withExclude.asJava
  }
}

object Immediate404Rule {
  def nextId: Long = Config.repos.flatMap(_.id).max + 1

  def fromJson(json: String): Immediate404Rule = {
    val map = Repox.gson.fromJson(json, classOf[java.util.Map[String, String]]).asScala
    Immediate404Rule(
      id = map.get("id").map(_.toLong),
      include = map("include"),
      exclude = map.get("exclude"),
      disabled = map("disabled").toBoolean
    )
  }
}

case class BlacklistRule(pattern: String, repoName: String)
