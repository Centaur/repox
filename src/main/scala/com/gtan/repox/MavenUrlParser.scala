package com.gtan.repox

import org.parboiled2._
import shapeless._

/**
 * val MavenFormat = """(/.+)+/((.+?)(_(.+?)(_(.+))?)?)/(.+?)/(\3-\8(-(.+?))?\.(.+))""".r
 * "/org/scala-lang/modules/scalajs/scalajs-sbt-plugin_2.10_0.13/0.5.6/scalajs-sbt-plugin-0.5.6.pom"
 * "/io/spray/sbt-revolver_2.10_0.13/0.7.2/sbt-revolver-0.7.2.pom"
 * "/org/scala-lang/modules/scalajs/scalajs-tools_2.10/0.5.6/scalajs-tools_2.10-0.5.6.pom"

 @param input url string
 */
class MavenUrlParser(val input: ParserInput) extends Parser {
  def id = rule {
    oneOrMore(CharPredicate.Printable)
  }

  def number = rule {
    oneOrMore(CharPredicate.Digit)
  }

  def version = rule {
    number ~ optional(oneOrMore('.' ~ number))
  }

  def crossBuildVersion = rule {
    '_' ~ version ~ optional('_' ~ version)
  }

  def ext = rule {
    oneOrMore(CharPredicate.Printable)
  }

  def root = rule {
    (oneOrMore('/' ~ id) ~ '/' ~ id ~ optional(crossBuildVersion) ~ '/' ~ version ~ '/' ~ id ~ '-' ~ version) ~> {
      (groupIds: List[String], artifactName: String, crossBuildVersion: Option[String], ver: String, artifactNameC: String, versionC: String) =>
        test(artifactName == artifactNameC && ver == versionC) ~ push(groupIds :: artifactName :: crossBuildVersion :: ver :: HNil)
    } ~ '.' ~ ext ~ EOI
  }

}
