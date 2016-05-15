package com.gtan.repox.config

import javax.sql.rowset.serial.SerialStruct

import com.gtan.repox.SerializationSupport
import com.gtan.repox.data.ExpireRule
import io.circe.Decoder.Result
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._


object ExpireRulePersister extends SerializationSupport {
  import com.gtan.repox.CirceCodecs.{durationDecoder, durationEncoder}

  case class NewOrUpdateExpireRule(rule: ExpireRule) extends ConfigCmd {
    override def transform(old: Config) = {
      val oldRules = old.expireRules
      old.copy(expireRules = rule.id.fold(oldRules :+ rule.copy(id = Some(ExpireRule.nextId.incrementAndGet()))) { _id =>
        oldRules.map {
          case r@ExpireRule(Some(`_id`), _, _, _) => rule
          case r => r
        }
      })
    }
  }

  case class EnableExpireRule(id: Long) extends ConfigCmd {
    override def transform(old: Config) = {
      val oldRules = old.expireRules
      old.copy(expireRules = oldRules.map {
        case p@ExpireRule(Some(`id`), _, _, _) => p.copy(disabled = false)
        case p => p
      })
    }
  }


  case class DisableExpireRule(id: Long) extends ConfigCmd {
    override def transform(old: Config) = {
      val oldRules = old.expireRules
      old.copy(expireRules = oldRules.map {
        case p@ExpireRule(Some(`id`), _, _, _) => p.copy(disabled = true)
        case p => p
      })
    }
  }


  case class DeleteExpireRule(id: Long) extends ConfigCmd {
    override def transform(old: Config) = {
      val oldRules = old.expireRules
      old.copy(
        expireRules = oldRules.filterNot(_.id.contains(id))
      )
    }
  }


  val NewOrUpdateExpireRuleClass = classOf[NewOrUpdateExpireRule].getName
  val EnableExpireRuleClass = classOf[EnableExpireRule].getName
  val DisableExpireRuleClass = classOf[DisableExpireRule].getName
  val DeleteExpireRuleClass = classOf[DeleteExpireRule].getName

  override val reader: Json => PartialFunction[String, Result[Jsonable]] = payload => {
    case NewOrUpdateExpireRuleClass => payload.as[NewOrUpdateExpireRule]
    case EnableExpireRuleClass => payload.as[EnableExpireRule]
    case DisableExpireRuleClass => payload.as[DisableExpireRule]
    case DeleteExpireRuleClass => payload.as[DeleteExpireRule]

  }

  import io.circe.syntax._
  override val writer: PartialFunction[Jsonable, Json] = {
    case o: NewOrUpdateExpireRule => o.asJson
    case o: EnableExpireRule => o.asJson
    case o: DisableExpireRule => o.asJson
    case o: DeleteExpireRule => o.asJson
  }
}
