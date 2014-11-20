name := "repox"

organization := "com.gtan"

scalaVersion := "2.11.4"

libraryDependencies ++= Seq(
  "io.undertow" % "undertow-core" % "1.1.0.Final" withSources(),
  "com.ning" % "async-http-client" % "1.8.14"
)

fork := true

Revolver.settings