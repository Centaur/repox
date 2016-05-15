package com.gtan.repox.config

import com.gtan.repox.SerializationSupport
import com.gtan.repox.data.Immediate404Rule
import io.circe.Decoder.Result
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

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

  case class EnableImmediate404Rule(id: Long) extends ConfigCmd {
    override def transform(old: Config) = {
      val oldRules = old.immediate404Rules
      old.copy(immediate404Rules = oldRules.map {
        case p@Immediate404Rule(Some(`id`), _, _, _) => p.copy(disabled = false)
        case p => p
      })
    }
  }

  case class DisableImmediate404Rule(id: Long) extends ConfigCmd {
    override def transform(old: Config) = {
      val oldRules = old.immediate404Rules
      old.copy(immediate404Rules = oldRules.map {
        case p@Immediate404Rule(Some(`id`), _, _, _) => p.copy(disabled = true)
        case p => p
      })
    }
  }

  case class DeleteImmediate404Rule(id: Long) extends ConfigCmd {
    override def transform(old: Config) = {
      val oldRules = old.immediate404Rules
      old.copy(
        immediate404Rules = oldRules.filterNot(_.id.contains(id))
      )
    }
  }

  val NewOrUpdateImmediate404RuleClass = classOf[NewOrUpdateImmediate404Rule].getName
  val EnableImmediate404RuleClass = classOf[EnableImmediate404Rule].getName
  val DisableImmediate404RuleClass = classOf[DisableImmediate404Rule].getName
  val DeleteImmediate404RuleClass = classOf[DeleteImmediate404Rule].getName

  override val reader: Json => PartialFunction[String, Result[Jsonable]] = payload => {
    case NewOrUpdateImmediate404RuleClass => payload.as[NewOrUpdateImmediate404Rule]
    case EnableImmediate404RuleClass => payload.as[EnableImmediate404Rule]
    case DisableImmediate404RuleClass => payload.as[DisableImmediate404Rule]
    case DeleteImmediate404RuleClass => payload.as[DeleteImmediate404Rule]
  }
  override val writer: PartialFunction[Jsonable, Json] = {
    case o: NewOrUpdateImmediate404Rule => o.asJson
    case o: EnableImmediate404Rule => o.asJson
    case o: DisableImmediate404Rule => o.asJson
    case o: DeleteImmediate404Rule => o.asJson
  }
}
