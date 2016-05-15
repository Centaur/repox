package com.gtan.repox.data

import java.util.concurrent.atomic.AtomicLong

import com.gtan.repox.config.Config

/**
 * Created by IntelliJ IDEA.
 * User: xf
 * Date: 14/11/23
 * Time: 下午12:15
 */
case class Immediate404Rule(id: Option[Long], include: String, exclude: Option[String] = None, disabled: Boolean = false) {
  def matches(uri: String): Boolean = {
    val included = uri.matches(include)
    exclude match {
      case None => included
      case Some(regex) =>
        val excluded = uri.matches(regex)
        included && !excluded
    }
  }

}

object Immediate404Rule {
  lazy val nextId: AtomicLong = new AtomicLong(Config.immediate404Rules.flatMap(_.id).reduceOption[Long](math.max).getOrElse(1))
}

case class BlacklistRule(pattern: String, repoName: String)
