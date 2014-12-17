package com.gtan.repox

import org.scalatest._

class RepoxSuite extends FunSuite {
  val mavenData = Map(
    "/org/scala-lang/modules/scalajs/scalajs-sbt-plugin_2.10_0.13/0.5.6/scalajs-sbt-plugin-0.5.6.pom" -> "/org.scala-lang.modules.scalajs/scalajs-sbt-plugin/scala_2.10/sbt_0.13/0.5.6/ivys/ivy.xml",
    "/io/spray/sbt-revolver_2.10_0.13/0.7.2/sbt-revolver-0.7.2.pom" -> "/io.spray/sbt-revolver/scala_2.10/sbt_0.13/0.7.2/ivys/ivy.xml",
    "/org/scala-lang/modules/scalajs/scalajs-tools_2.10/0.5.6/scalajs-tools_2.10-0.5.6.pom" -> "/org.scala-lang.modules.scalajs/scalajs-tools_2.10/0.5.6/ivys/ivy.xml",
    "/com/eed3si9n/sbt-assembly_2.10_0.13/0.12.0/sbt-assembly-0.12.0.pom" -> "/com.eed3si9n/sbt-assembly/scala_2.10/sbt_0.13/0.12.0/ivys/ivy.xml",
    "/org/scala-sbt/sbt/0.13.7/sbt-0.13.7.pom" -> "/org.scala-sbt/sbt/0.13.7/ivys/ivy.xml",
    "/org/scala-sbt/main/0.13.7/main-0.13.7.pom" -> "/org.scala-sbt/main/0.13.7/ivys/ivy.xml",
    "/org/scala-sbt/actions/0.13.7/actions-0.13.7.pom" -> "/org.scala-sbt/actions/0.13.7/ivys/ivy.xml"
    , "/net/virtual-void/sbt-dependency-graph/0.7.4/sbt-dependency-graph-0.7.4.jar" -> "/net/virtual-void/sbt-dependency-graph_2.10_0.13/0.7.4/sbt-dependency-graph-0.7.4.jar"
    , "/com/github/mpeltonen/sbt-idea/1.6.0/sbt-idea-1.6.0.jar" -> "/com/github/mpeltonen/sbt-idea_2.10_0.13/1.6.0/sbt-idea-1.6.0.jar"
  )

  val ivyData = Map(
    "/net.virtual-void/sbt-dependency-graph/0.7.4/jars/sbt-dependency-graph.jar" -> "/net/virtual-void/sbt-dependency-graph_2.10_0.13/0.7.4/sbt-dependency-graph-0.7.4.jar"
    ,"/com.github.mpeltonen/sbt-idea/1.6.0/jars/sbt-idea.jar" -> "/com/github/mpeltonen/sbt-idea_2.10_0.13/1.6.0/sbt-idea-1.6.0.jar"
  )

  test("MavenFormat should match keys of mavenData") {
    assert(mavenData.keys.forall(_.matches(Repox.MavenFormat.regex)))
  }

  test("IvyFormat should match keys of ivyData") {
    assert(ivyData.keys.forall(_.matches(Repox.IvyFormat.regex)))
  }

  test("keys in all data map should have the value as peer") {
    assert((mavenData ++ ivyData).forall { case (k, v) =>
      Repox.peer(k).contains(v)
    })
  }
}
