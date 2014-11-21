name := "repox"

organization := "com.gtan"

scalaVersion := "2.11.4"

libraryDependencies ++= Seq(
  "io.undertow" % "undertow-core" % "1.1.0.Final" withSources(),
  "com.ning" % "async-http-client" % "1.8.14",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
  "ch.qos.logback" % "logback-classic" % "1.1.2"
)

fork := true

Revolver.settings