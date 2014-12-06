package com.gtan.repox.data

import com.gtan.repox.Repox

import scala.concurrent.duration.Duration

import collection.JavaConverters._

case class ExpireRule(id: Option[Long], pattern: String, duration: Duration) {
  def toMap: java.util.Map[String, Any] = {
    val withoutId: Map[String, Any] = Map(
      "pattern" -> pattern,
      "duration" -> duration.toString
    )
    id.fold(withoutId) { _id =>
      withoutId.updated("id", _id)
    } asJava
  }
}

object ExpireRule {
  def fromJson(json: String): ExpireRule = {
    val map = Repox.gson.fromJson(json, classOf[java.util.Map[String, String]]).asScala
    ExpireRule(
      id = map.get("id").map(_.toLong),
      pattern = map("pattern"),
      duration = Duration.apply(map("duration"))
    )
  }
}
