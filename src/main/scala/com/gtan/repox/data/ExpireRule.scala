package com.gtan.repox.data

import com.gtan.repox.Repox
import com.gtan.repox.config.Config
import play.api.libs.json._

import scala.concurrent.duration.Duration

import collection.JavaConverters._

case class ExpireRule(id: Option[Long], pattern: String, duration: Duration, disabled: Boolean = false)

object ExpireRule {

  import DurationFormat._

  def nextId: Long = Config.expireRules.flatMap(_.id).max + 1

  implicit val format = Json.format[ExpireRule]
}
