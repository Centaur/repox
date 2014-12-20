package com.gtan.repox.config

import javax.sql.rowset.serial.SerialStruct

import com.gtan.repox.SerializationSupport
import com.gtan.repox.data.ExpireRule
import play.api.libs.json.{JsValue, Json}

trait ExpireRulePersister {

  case class NewOrUpdateExpireRule(rule: ExpireRule) extends Cmd {
    override def transform(old: Config) = {
      val oldRules = old.expireRules
      old.copy(expireRules = rule.id.fold(oldRules :+ rule.copy(id = Some(ExpireRule.nextId))) { _id =>
        oldRules.map {
          case r@ExpireRule(Some(`_id`), _, _, _) => rule
          case r => r
        }
      })
    }
  }

  object NewOrUpdateExpireRule {
    implicit val format = Json.format[NewOrUpdateExpireRule]
  }

  case class EnableExpireRule(id: Long) extends Cmd {
    override def transform(old: Config) = {
      val oldRules = old.expireRules
      old.copy(expireRules = oldRules.map {
        case p@ExpireRule(Some(`id`), _, _, _) => p.copy(disabled = false)
        case p => p
      })
    }
  }

  object EnableExpireRule {
    implicit val format = Json.format[EnableExpireRule]
  }

  case class DisableExpireRule(id: Long) extends Cmd {
    override def transform(old: Config) = {
      val oldRules = old.expireRules
      old.copy(expireRules = oldRules.map {
        case p@ExpireRule(Some(`id`), _, _, _) => p.copy(disabled = true)
        case p => p
      })
    }
  }

  object DisableExpireRule {
    implicit val format = Json.format[DisableExpireRule]
  }

  case class DeleteExpireRule(id: Long) extends Cmd {
    override def transform(old: Config) = {
      val oldRules = old.expireRules
      old.copy(
        expireRules = oldRules.filterNot(_.id == Some(id))
      )
    }
  }

  object DeleteExpireRule {
    implicit val format = Json.format[DeleteExpireRule]
  }

}

object ExpireRulePersister extends SerializationSupport {

  import ConfigPersister._

  val NewOrUpdateExpireRuleClass = classOf[NewOrUpdateExpireRule].getName
  val EnableExpireRuleClass      = classOf[EnableExpireRule].getName
  val DisableExpireRuleClass     = classOf[DisableExpireRule].getName
  val DeleteExpireRuleClass      = classOf[DeleteExpireRule].getName

  override val reader: (JsValue) => PartialFunction[String, Cmd] = payload => {
    case NewOrUpdateExpireRuleClass => payload.as[NewOrUpdateExpireRule]
    case EnableExpireRuleClass => payload.as[EnableExpireRule]
    case DisableExpireRuleClass => payload.as[DisableExpireRule]
    case DeleteExpireRuleClass => payload.as[DeleteExpireRule]

  }
  override val writer  : PartialFunction[Cmd, JsValue]             = {
    case o: NewOrUpdateExpireRule => Json.toJson(o)
    case o: EnableExpireRule => Json.toJson(o)
    case o: DisableExpireRule => Json.toJson(o)
    case o: DeleteExpireRule => Json.toJson(o)
  }
}
