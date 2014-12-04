package com.gtan.repox

import com.gtan.repox.config.Config

/**
 * Created by IntelliJ IDEA.
 * User: xf
 * Date: 14/11/23
 * Time: 下午12:15
 */
case class Immediate404Rule(id: Long, include: String, exclude: Option[String] = None) {
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
  def nextId: Long = Config.repos.map(_.id).max + 1

}

case class BlacklistRule(pattern: String, repoName: String)
