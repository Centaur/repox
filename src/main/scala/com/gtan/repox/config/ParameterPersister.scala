package com.gtan.repox.config

import com.gtan.repox.SerializationSupport
import io.circe.Decoder.Result

import scala.concurrent.duration.Duration
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._


object ParameterPersister extends SerializationSupport {

  import com.gtan.repox.CirceCodecs.{durationDecoder, durationEncoder}

  case class SetHeadTimeout(m: Duration) extends ConfigCmd {
    override def transform(old: Config) = {
      old.copy(headTimeout = m)
    }
  }


  case class SetHeadRetryTimes(m: Int) extends ConfigCmd {
    override def transform(old: Config) = {
      old.copy(headRetryTimes = m)
    }
  }

  case class ModifyPassword(newPassword: String) extends ConfigCmd {
    override def transform(old: Config): Config = {
      old.copy(password = newPassword)
    }
  }

  case class SetExtraResources(value: String) extends ConfigCmd {
    override def transform(old: Config): Config = {
      old.copy(extraResources = value.split(":"))
    }
  }

  val SetHeadTimeoutClass = classOf[SetHeadTimeout].getName
  val SetHeadRetryTimesClass = classOf[SetHeadRetryTimes].getName
  val ModifyPasswordClass = classOf[ModifyPassword].getName
  val SetExtraResourcesClass = classOf[SetExtraResources].getName

  override val reader: Json => PartialFunction[String, Result[Jsonable]] = payload => {
    case SetHeadTimeoutClass => payload.as[SetHeadTimeout]
    case SetHeadRetryTimesClass => payload.as[SetHeadRetryTimes]
    case ModifyPasswordClass => payload.as[ModifyPassword]
    case SetExtraResourcesClass => payload.as[SetExtraResources]
  }
  override val writer: PartialFunction[Jsonable, Json] = {
    case o: SetHeadTimeout => o.asJson
    case o: SetHeadRetryTimes => o.asJson
    case o: ModifyPassword => o.asJson
    case o: SetExtraResources => o.asJson
  }
}
