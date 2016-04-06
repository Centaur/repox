package com.gtan.repox.data

import java.util.concurrent.atomic.AtomicLong

import com.gtan.repox.Repox
import com.gtan.repox.config.Config
import play.api.libs.json._

import scala.concurrent.duration.Duration

import collection.JavaConverters._

case class ExpireRule(id: Option[Long], pattern: String, duration: Duration, disabled: Boolean = false)

object ExpireRule {

  import DurationFormat._

  lazy val nextId: AtomicLong = new AtomicLong(Config.expireRules.flatMap(_.id).reduceOption[Long](math.max).getOrElse(1))

  implicit val format = Json.format[ExpireRule]
}
