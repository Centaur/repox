package com.gtan.repox.config

import akka.actor.ActorLogging
import akka.persistence.PersistentView

/**
 * Created by IntelliJ IDEA.
 * User: xf
 * Date: 14/11/30
 * Time: 下午2:58
 */

object ConfigView {
  case object Get
}
class ConfigView extends PersistentView with ActorLogging{

  override val viewId = "ConfigViewer"
  override val persistenceId = "Config"

  def receive: Receive = {
    case ConfigChanged(_, cmd) => log.debug(s"ConfigView received event caused by cmd: $cmd")
    case UseDefault => log.debug(s"ConfigView received UseDefault evt")
  }

}
