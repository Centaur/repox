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
  val validIdChars = CharPredicate.Printable -- '_' -- '/' -- '.'
  def id = rule {
    capture(validIdChars +)
  }

  def number = rule {
    CharPredicate.Digit +
  }

  def version = rule {
    number + '.'
  }

  def crossBuildVersion = rule {
    capture(('_' ~ version) +)
  }

  def ext = rule {
    CharPredicate.Alpha +
  }

  def root: RuleN[Seq[String]::String::Option[String]::String::HNil] = rule {
    oneOrMore('/' ~ id) ~ optional(crossBuildVersion) ~ '/' ~ capture(version) ~> {
      (groupIdsConsArtifactName: Seq[String], crossBuildVersion: Option[String], ver: String) =>
        val (groupIds, artifactName) = (groupIdsConsArtifactName.init, groupIdsConsArtifactName.last)
        run('/' ~ str(artifactName) ~ '-' ~ str(ver) ~ '.' ~ ext ~ EOI) ~ push(groupIds :: artifactName :: crossBuildVersion :: ver :: HNil)
    }
  }

}
