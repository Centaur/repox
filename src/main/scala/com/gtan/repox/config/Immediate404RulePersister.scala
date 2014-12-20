package com.gtan.repox.config

import com.gtan.repox.SerializationSupport
import com.gtan.repox.data.Immediate404Rule
import play.api.libs.json.{JsValue, Json}

trait Immediate404RulePersister {

  case class NewOrUpdateImmediate404Rule(rule: Immediate404Rule) extends Cmd {
    override def transform(old: Config) = {
      val oldRules = old.immediate404Rules
      old.copy(immediate404Rules = rule.id.fold(oldRules :+ rule.copy(id = Some(Immediate404Rule.nextId))) { _id =>
        oldRules.map {
          case r@Immediate404Rule(Some(`_id`), _, _, _) => rule
          case r => r
        }
      })
    }
  }

  object NewOrUpdateImmediate404Rule {
    implicit val format = Json.format[NewOrUpdateImmediate404Rule]
  }

  case class EnableImmediate404Rule(id: Long) extends Cmd {
    override def transform(old: Config) = {
      val oldRules = old.immediate404Rules
      old.copy(immediate404Rules = oldRules.map {
        case p@Immediate404Rule(Some(`id`), _, _, _) => p.copy(disabled = false)
        case p => p
      })
    }
  }

  object EnableImmediate404Rule {
    implicit val format = Json.format[EnableImmediate404Rule]
  }

  case class DisableImmediate404Rule(id: Long) extends Cmd {
    override def transform(old: Config) = {
      val oldRules = old.immediate404Rules
      old.copy(immediate404Rules = oldRules.map {
        case p@Immediate404Rule(Some(`id`), _, _, _) => p.copy(disabled = true)
        case p => p
      })
    }
  }

  object DisableImmediate404Rule {
    implicit val format = Json.format[DisableImmediate404Rule]
  }

  case class DeleteImmediate404Rule(id: Long) extends Cmd {
    override def transform(old: Config) = {
      val oldRules = old.immediate404Rules
      old.copy(
        immediate404Rules = oldRules.filterNot(_.id == Some(id))
      )
    }
  }

  object DeleteImmediate404Rule {
    implicit val format = Json.format[DeleteImmediate404Rule]
  }

}

object Immediate404RulePersister extends SerializationSupport {

  import ConfigPersister._

  val NewOrUpdateImmediate404RuleClass = classOf[NewOrUpdateImmediate404Rule].getName
  val EnableImmediate404RuleClass      = classOf[EnableImmediate404Rule].getName
  val DisableImmediate404RuleClass     = classOf[DisableImmediate404Rule].getName
  val DeleteImmediate404RuleClass      = classOf[DeleteImmediate404Rule].getName

  override val reader: (JsValue) => PartialFunction[String, Cmd] = payload => {
    case NewOrUpdateImmediate404RuleClass => payload.as[NewOrUpdateImmediate404Rule]
    case EnableImmediate404RuleClass => payload.as[EnableImmediate404Rule]
    case DisableImmediate404RuleClass => payload.as[DisableImmediate404Rule]
    case DeleteImmediate404RuleClass => payload.as[DeleteImmediate404Rule]
  }
  override val writer  : PartialFunction[Cmd, JsValue]             = {
    case o: NewOrUpdateImmediate404Rule => Json.toJson(o)
    case o: EnableImmediate404Rule => Json.toJson(o)
    case o: DisableImmediate404Rule => Json.toJson(o)
    case o: DeleteImmediate404Rule => Json.toJson(o)
  }
}
