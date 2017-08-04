package com.gtan.repox.data

import java.util.concurrent.atomic.AtomicLong

import com.gtan.repox.Repox
import com.gtan.repox.config.Config
import io.circe.generic.JsonCodec
import play.api.libs.json._

import scala.concurrent.duration.Duration
import collection.JavaConverters._

import io.circe.generic.semiauto._

case class ExpireRule(id: Option[Long], pattern: String, duration: Duration, disabled: Boolean = false)

object ExpireRule extends DurationFormat{

  lazy val nextId: AtomicLong = new AtomicLong(Config.expireRules.flatMap(_.id).reduceOption[Long](math.max).getOrElse(1))

  implicit val format = Json.format[ExpireRule]

  implicit val expireRuleEncoder = deriveEncoder[ExpireRule]
  implicit val expireRuleDecoder = deriveDecoder[ExpireRule]

}
