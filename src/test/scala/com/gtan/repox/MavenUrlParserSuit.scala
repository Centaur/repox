package com.gtan.repox

import org.parboiled2.{Rule, Parser, ParseError}
import org.scalatest.{Matchers, FunSuite}
import shapeless._

import scala.util.{Try, Success, Failure}

class MavenUrlParserSuit extends FunSuite with Matchers {

  test("testRoot") {
    val parser = new MavenUrlParser("/org/scala-lang/modules/scalajs/scalajs-sbt-plugin_2.10_0.13/0.5.6/scalajs-sbt-plugin-0.5.6.pom")

    var result = parser.root.run()
    println(result)
    result match {
      case Failure(error: ParseError) => println(parser.formatError(error))
      case _ =>
    }
    result should matchPattern {
      case Success(Seq("org", "scala-lang", "modules", "scalajs") :: "scalajs-sbt-plugin" :: Some("_2.10_0.13") :: "0.5.6" :: HNil) =>
    }
  }

}
