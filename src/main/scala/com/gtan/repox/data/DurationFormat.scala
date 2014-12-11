package com.gtan.repox.data

import play.api.libs.json._

import scala.concurrent.duration.Duration

object DurationFormat {
  implicit val durationFormat = new Format[Duration] {
    override def reads(json: JsValue) = json match {
      case JsString(str) =>
        JsSuccess(Duration(str))
      case _ =>
        JsError("duration json format need string")
    }

    override def writes(o: Duration) = JsString(o.toString)
  }

}
