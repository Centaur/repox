package com.gtan.repox.data

import io.circe.{Decoder, Encoder}
import play.api.libs.json._

import scala.concurrent.duration.Duration

trait DurationFormat {
  implicit val durationFormat = new Format[Duration] {
    override def reads(json: JsValue) = json match {
      case JsString(str) =>
        JsSuccess(Duration(str))
      case _ =>
        JsError("duration json format need string")
    }

    override def writes(o: Duration) = JsString(o.toString)
  }

  implicit val encodeDuration: Encoder[Duration] = Encoder.encodeString.contramap[Duration](_.toString)
  implicit val decodeDuration: Decoder[Duration] = Decoder.decodeString.emap { str =>
    Right(Duration(str))
  }

}
