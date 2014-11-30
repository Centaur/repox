package com.gtan.repox

import akka.actor.ActorLogging
import akka.persistence.{SnapshotOffer, PersistentView}

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
  import ConfigView._

  override val viewId = "ConfigViewer"
  override val persistenceId = "Config"

  def receive: Receive = {
    case msg => log.debug(s"ConfigView received message: $msg")
  }

}
