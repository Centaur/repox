package com.gtan.repox.config

import javax.sql.rowset.serial.SerialStruct

import com.gtan.repox.SerializationSupport
import com.gtan.repox.data.ExpireRule
import play.api.libs.json.{JsValue, Json}

object ExpireRulePersister extends SerializationSupport {

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

  implicit val NewOrUpdateExpireRuleFormat = Json.format[NewOrUpdateExpireRule]

  case class EnableExpireRule(id: Long) extends ConfigCmd {
    override def transform(old: Config) = {
      val oldRules = old.expireRules
      old.copy(expireRules = oldRules.map {
        case p@ExpireRule(Some(`id`), _, _, _) => p.copy(disabled = false)
        case p => p
      })
    }
  }

  implicit val EnableExpireRuleFormat = Json.format[EnableExpireRule]

  case class DisableExpireRule(id: Long) extends ConfigCmd {
    override def transform(old: Config) = {
      val oldRules = old.expireRules
      old.copy(expireRules = oldRules.map {
        case p@ExpireRule(Some(`id`), _, _, _) => p.copy(disabled = true)
        case p => p
      })
    }
  }

  implicit val DisalbExpireRuleFormat = Json.format[DisableExpireRule]

  case class DeleteExpireRule(id: Long) extends ConfigCmd {
    override def transform(old: Config) = {
      val oldRules = old.expireRules
      old.copy(
        expireRules = oldRules.filterNot(_.id.contains(id))
      )
    }
  }

  implicit val DeleteExpireRuleFormat = Json.format[DeleteExpireRule]

  val NewOrUpdateExpireRuleClass = classOf[NewOrUpdateExpireRule].getName
  val EnableExpireRuleClass = classOf[EnableExpireRule].getName
  val DisableExpireRuleClass = classOf[DisableExpireRule].getName
  val DeleteExpireRuleClass = classOf[DeleteExpireRule].getName

  override val reader: (JsValue) => PartialFunction[String, Jsonable] = payload => {
    case NewOrUpdateExpireRuleClass => payload.as[NewOrUpdateExpireRule]
    case EnableExpireRuleClass => payload.as[EnableExpireRule]
    case DisableExpireRuleClass => payload.as[DisableExpireRule]
    case DeleteExpireRuleClass => payload.as[DeleteExpireRule]

  }
  override val writer: PartialFunction[Jsonable, JsValue] = {
    case o: NewOrUpdateExpireRule => Json.toJson(o)
    case o: EnableExpireRule => Json.toJson(o)
    case o: DisableExpireRule => Json.toJson(o)
    case o: DeleteExpireRule => Json.toJson(o)
  }
}
