package com.gtan.repox.config

import com.gtan.repox.data.ExpireRule

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

  case class EnableExpireRule(id: Long) extends Cmd {
    override def transform(old: Config) = {
      val oldRules = old.expireRules
      old.copy(expireRules = oldRules.map {
        case p@ExpireRule(Some(`id`), _, _, _) => p.copy(disabled = false)
        case p => p
      })
    }
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

  case class DeleteExpireRule(id: Long) extends Cmd {
    override def transform(old: Config) = {
      val oldRules = old.expireRules
      old.copy(
        expireRules = oldRules.filterNot(_.id == Some(id))
      )
    }
  }

}
