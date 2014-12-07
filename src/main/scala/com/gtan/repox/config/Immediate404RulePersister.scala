package com.gtan.repox.config

import com.gtan.repox.data.Immediate404Rule

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

  case class EnableImmediate404Rule(id: Long) extends Cmd {
    override def transform(old: Config) = {
      val oldRules = old.immediate404Rules
      old.copy(immediate404Rules = oldRules.map {
        case p@Immediate404Rule(Some(`id`), _, _, _) => p.copy(disabled = false)
        case p => p
      })
    }
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

  case class DeleteImmediate404Rule(id: Long) extends Cmd {
    override def transform(old: Config) = {
      val oldRules = old.immediate404Rules
      old.copy(
        immediate404Rules = oldRules.filterNot(_.id == Some(id))
      )
    }
  }


}
