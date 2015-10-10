package com.gtan.repox.config

import com.gtan.repox.SerializationSupport
import com.gtan.repox.data.DurationFormat
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.duration.Duration


object ParameterPersister extends SerializationSupport {

  case class SetHeadTimeout(m: Duration) extends ConfigCmd {
    override def transform(old: Config) = {
      old.copy(headTimeout = m)
    }
  }


  import DurationFormat._
  implicit val SetHeadTimeoutFormat = Json.format[SetHeadTimeout]

  case class SetHeadRetryTimes(m: Int) extends ConfigCmd {
    override def transform(old: Config) = {
      old.copy(headRetryTimes = m)
    }
  }

  implicit val SetHeadRetryTimesFormat = Json.format[SetHeadRetryTimes]

  case class ModifyPassword(newPassword: String) extends ConfigCmd {
    override def transform(old: Config): Config = {
      old.copy(password = newPassword)
    }
  }

  implicit val ModifyPasswordFormat = Json.format[ModifyPassword]

  case class SetExtraResources(value: String) extends ConfigCmd {
    override def transform(old: Config): Config = {
      old.copy(extraResources = value.split(":"))
    }
  }

  implicit val SetExtraResourcesFormat = Json.format[SetExtraResources]

  val SetHeadTimeoutClass = classOf[SetHeadTimeout].getName
  val SetHeadRetryTimesClass = classOf[SetHeadRetryTimes].getName
  val ModifyPasswordClass = classOf[ModifyPassword].getName
  val SetExtraResourcesClass = classOf[SetExtraResources].getName

  override val reader: (JsValue) => PartialFunction[String, Jsonable] = payload => {
    case SetHeadTimeoutClass => payload.as[SetHeadTimeout]
    case SetHeadRetryTimesClass => payload.as[SetHeadRetryTimes]
    case ModifyPasswordClass => payload.as[ModifyPassword]
    case SetExtraResourcesClass => payload.as[SetExtraResources]
  }
  override val writer: PartialFunction[Jsonable, JsValue] = {
    case o: SetHeadTimeout => Json.toJson(o)
    case o: SetHeadRetryTimes => Json.toJson(o)
    case o: ModifyPassword => Json.toJson(o)
    case o: SetExtraResources => Json.toJson(o)
  }
}
