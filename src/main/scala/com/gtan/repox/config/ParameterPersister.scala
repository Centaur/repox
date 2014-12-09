package com.gtan.repox.config

import scala.concurrent.duration.Duration

trait ParameterPersister {
  case class SetConnectionTimeout(d: Duration) extends Cmd {
    override def transform(old: Config) = {
      old.copy(connectionTimeout = d)
    }
  }

  case class SetConnectionIdleTimeout(d: Duration) extends Cmd {
    override def transform(old: Config) = {
      old.copy(connectionIdleTimeout = d)
    }
  }
  case class SetMainClientMaxConnections(m: Int) extends Cmd {
    override def transform(old: Config) = {
      old.copy(mainClientMaxConnections = m)
    }
  }
  case class SetMainClientMaxConnectionsPerHost(m: Int) extends Cmd {
    override def transform(old: Config) = {
      old.copy(mainClientMaxConnectionsPerHost = m)
    }
  }
  case class SetProxyClientMaxConnections(m: Int) extends Cmd {
    override def transform(old: Config) = {
      old.copy(proxyClientMaxConnections = m)
    }
  }
  case class SetProxyClientMaxConnectionsPerHost(m: Int) extends Cmd {
    override def transform(old: Config) = {
      old.copy(proxyClientMaxConnectionsPerHost = m)
    }
  }
  case class SetHeadTimeout(m: Duration) extends Cmd {
    override def transform(old: Config) = {
      old.copy(headTimeout = m)
    }
  }
  case class SetGetDataTimeout(m: Duration) extends Cmd {
    override def transform(old: Config) = {
      old.copy(getDataTimeout = m)
    }
  }
  case class SetHeadRetryTimes(m: Int) extends Cmd {
    override def transform(old: Config) = {
      old.copy(headRetryTimes = m)
    }
  }

}
