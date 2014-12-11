package com.gtan.repox.config

import scala.concurrent.duration.Duration

trait ParameterPersister {
  case class SetHeadTimeout(m: Duration) extends Cmd {
    override def transform(old: Config) = {
      old.copy(headTimeout = m)
    }
  }

  case class SetHeadRetryTimes(m: Int) extends Cmd {
    override def transform(old: Config) = {
      old.copy(headRetryTimes = m)
    }
  }

}
