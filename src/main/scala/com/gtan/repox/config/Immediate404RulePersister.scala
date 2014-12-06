package com.gtan.repox.config

import com.gtan.repox.data.Immediate404Rule

trait Immediate404RulePersister {
  case class NewImmediate404Rule(rule: Immediate404Rule) extends Cmd {
    override def transform(old: Config) = {
      val oldRules = old.immediate404Rules
      old.copy(immediate404Rules = oldRules :+ rule)
    }
  }


}
