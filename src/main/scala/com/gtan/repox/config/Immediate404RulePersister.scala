package com.gtan.repox.config

import com.gtan.repox.SerializationSupport
import com.gtan.repox.data.Immediate404Rule
import play.api.libs.json.{JsValue, Json}

object Immediate404RulePersister extends SerializationSupport {

  case class NewOrUpdateImmediate404Rule(rule: Immediate404Rule) extends ConfigCmd {
    override def transform(old: Config) = {
      val oldRules = old.immediate404Rules
      old.copy(immediate404Rules = rule.id.fold(oldRules :+ rule.copy(id = Some(Immediate404Rule.nextId.incrementAndGet()))) { _id =>
        oldRules.map {
          case r@Immediate404Rule(Some(`_id`), _, _, _) => rule
          case r => r
        }
      })
    }
  }

  implicit val newOrUpdateImmediate404RuleFormat = Json.format[NewOrUpdateImmediate404Rule]

  case class EnableImmediate404Rule(id: Long) extends ConfigCmd {
    override def transform(old: Config) = {
      val oldRules = old.immediate404Rules
      old.copy(immediate404Rules = oldRules.map {
        case p@Immediate404Rule(Some(`id`), _, _, _) => p.copy(disabled = false)
        case p => p
      })
    }
  }

  implicit val enableImmediate404RuleFormat = Json.format[EnableImmediate404Rule]

  case class DisableImmediate404Rule(id: Long) extends ConfigCmd {
    override def transform(old: Config) = {
      val oldRules = old.immediate404Rules
      old.copy(immediate404Rules = oldRules.map {
        case p@Immediate404Rule(Some(`id`), _, _, _) => p.copy(disabled = true)
        case p => p
      })
    }
  }

  implicit val DisableImmediate404RuleFormat = Json.format[DisableImmediate404Rule]

  case class DeleteImmediate404Rule(id: Long) extends ConfigCmd {
    override def transform(old: Config) = {
      val oldRules = old.immediate404Rules
      old.copy(
        immediate404Rules = oldRules.filterNot(_.id.contains(id))
      )
    }
  }

  implicit val DeleteImmediat404RuleFormat = Json.format[DeleteImmediate404Rule]

  val NewOrUpdateImmediate404RuleClass = classOf[NewOrUpdateImmediate404Rule].getName
  val EnableImmediate404RuleClass = classOf[EnableImmediate404Rule].getName
  val DisableImmediate404RuleClass = classOf[DisableImmediate404Rule].getName
  val DeleteImmediate404RuleClass = classOf[DeleteImmediate404Rule].getName

  override val reader: (JsValue) => PartialFunction[String, Jsonable] = payload => {
    case NewOrUpdateImmediate404RuleClass => payload.as[NewOrUpdateImmediate404Rule]
    case EnableImmediate404RuleClass => payload.as[EnableImmediate404Rule]
    case DisableImmediate404RuleClass => payload.as[DisableImmediate404Rule]
    case DeleteImmediate404RuleClass => payload.as[DeleteImmediate404Rule]
  }
  override val writer: PartialFunction[Jsonable, JsValue] = {
    case o: NewOrUpdateImmediate404Rule => Json.toJson(o)
    case o: EnableImmediate404Rule => Json.toJson(o)
    case o: DisableImmediate404Rule => Json.toJson(o)
    case o: DeleteImmediate404Rule => Json.toJson(o)
  }
}
