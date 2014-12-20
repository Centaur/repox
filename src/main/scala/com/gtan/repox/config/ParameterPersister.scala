package com.gtan.repox.config

import com.gtan.repox.SerializationSupport
import com.gtan.repox.data.DurationFormat
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.duration.Duration

trait ParameterPersister {
  case class SetHeadTimeout(m: Duration) extends Cmd {
    override def transform(old: Config) = {
      old.copy(headTimeout = m)
    }
  }

  object SetHeadTimeout {
    import DurationFormat._
    implicit val format = Json.format[SetHeadTimeout]
  }

  case class SetHeadRetryTimes(m: Int) extends Cmd {
    override def transform(old: Config) = {
      old.copy(headRetryTimes = m)
    }
  }

  object SetHeadRetryTimes {
    implicit val format = Json.format[SetHeadRetryTimes]
  }

  case class ModifyPassword(newPassword: String) extends Cmd {
    override def transform(old: Config): Config = {
      old.copy(password = newPassword)
    }
  }

  object ModifyPassword {
    implicit val format = Json.format[ModifyPassword]
  }

  case class SetExtraResources(value: String) extends Cmd {
    override def transform(old: Config): Config = {
      old.copy(extraResources = value.split(":"))
    }
  }

  object SetExtraResources {
    implicit val format = Json.format[SetExtraResources]
  }
}

object ParameterPersister extends SerializationSupport {
  import ConfigPersister._

  val SetHeadTimeoutClass              = classOf[SetHeadTimeout].getName
  val SetHeadRetryTimesClass           = classOf[SetHeadRetryTimes].getName
  val ModifyPasswordClass              = classOf[ModifyPassword].getName
  val SetExtraResourcesClass = classOf[SetExtraResources].getName

  override val reader: (JsValue) => PartialFunction[String, Cmd] = payload => {
    case SetHeadTimeoutClass => payload.as[SetHeadTimeout]
    case SetHeadRetryTimesClass => payload.as[SetHeadRetryTimes]
    case ModifyPasswordClass => payload.as[ModifyPassword]
    case SetExtraResourcesClass => payload.as[SetExtraResources]
  }
  override val writer  : PartialFunction[Cmd, JsValue]             = {
    case o: SetHeadTimeout => Json.toJson(o)
    case o: SetHeadRetryTimes => Json.toJson(o)
    case o: ModifyPassword => Json.toJson(o)
    case o: SetExtraResources => Json.toJson(o)
  }
}
