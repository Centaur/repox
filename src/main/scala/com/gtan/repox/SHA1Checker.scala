package com.gtan.repox

import java.nio.file.Path

import akka.actor.{ActorLogging, Actor}

/**
 * Created by IntelliJ IDEA.
 * User: xf
 * Date: 14/11/29
 * Time: 下午3:24
 */
object SHA1Checker {
  case class Check(path: Path)
}
class SHA1Checker extends Actor with ActorLogging{
  import SHA1Checker._

  override def receive = {
    case Check(path) =>

  }
}
