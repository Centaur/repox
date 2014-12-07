package com.gtan.repox.data

import com.gtan.repox.Repox
import com.gtan.repox.config.Config
import play.api.libs.json._

import scala.concurrent.duration.Duration

import collection.JavaConverters._

case class ExpireRule(id: Option[Long], pattern: String, duration: Duration, disabled: Boolean = false)

object ExpireRule {

  def nextId: Long = Config.expireRules.flatMap(_.id).max + 1

  implicit val durationFormat = new Format[Duration] {
    override def reads(json: JsValue) = json match {
      case JsString(str) =>
        JsSuccess(Duration(str))
      case _ =>
        JsError("duration json format need string")
    }

    override def writes(o: Duration) = JsString(o.toString)
  }
  implicit val format = Json.format[ExpireRule]
}
