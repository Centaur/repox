name := "repox"

organization := "com.gtan"

scalaVersion := "2.12.4"

val akkaVersion = "2.5.7"

libraryDependencies ++= {
  val undertowVer = "1.4.20.Final"
  val logbackVer = "1.2.3"
  val leveldbVer = "0.7"
  val leveldbjniVer = "1.8"
  val scalaTestVer = "3.0.4"
  val playJsonVer = "2.6.7"
  val scalaLoggingVer = "3.7.2"
  val ningVer = "1.9.40"
  val protobufVer = "3.4.0"
  Seq(
    "io.undertow" % "undertow-core" % undertowVer,
    ("com.ning" % "async-http-client" % ningVer)
      .exclude("org.slf4j", "slf4j-api"),
    ("com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVer)
      .exclude("org.scala-lang", "scala-library")
      .exclude("org.scala-lang", "scala-reflect"),
    ("ch.qos.logback" % "logback-classic" % logbackVer)
      .exclude("org.slf4j", "slf4j-api"),
    ("com.typesafe.akka" %% "akka-actor" % akkaVersion)
      .exclude("org.scala-lang", "scala-library")
      .exclude("org.slf4j", "slf4j-api"),
    ("com.typesafe.akka" %% "akka-slf4j" % akkaVersion)
      .exclude("org.scala-lang", "scala-library")
      .exclude("org.slf4j", "slf4j-api"),
    ("com.typesafe.akka" %% "akka-agent" % akkaVersion)
      .exclude("org.scala-lang", "scala-library"),
    ("com.typesafe.akka" %% "akka-persistence" % akkaVersion)
      .exclude("org.scala-lang", "scala-library"),
    ("org.iq80.leveldb" % "leveldb" % leveldbVer)
      .exclude("com.google.guava", "guava"),
    "org.fusesource.leveldbjni" % "leveldbjni-all" % leveldbjniVer,
    ("com.typesafe.akka" %% "akka-persistence-query" % akkaVersion)
      .exclude("org.scala-lang", "scala-library")
      .exclude("com.typesafe", "config")
      .exclude("com.typesafe", "ssl-config-akka_2.12")
      .exclude("com.typesafe.akka", "akka-testkit_2.12")
      .exclude("com.typesafe.akka", "akka-stream-testkit_2.12"),
    ("com.typesafe.play" %% "play-json" % playJsonVer)
      .exclude("org.scala-lang", "scala-library"),
    "com.google.protobuf" % "protobuf-java" % protobufVer,
    "org.scalatest" %% "scalatest" % scalaTestVer % "test"
  )
}

transitiveClassifiers := Seq("sources")
updateOptions := updateOptions.value.withGigahorse(false)
scalacOptions ++= Seq(
                       "-feature",
                       "-deprecation",
                       "-language:implicitConversions",
                       //  "-language:higherKinds",
                       //  "-language:existentials",
                       "-language:postfixOps"
                     )

fork := true

assemblyMergeStrategy in assembly := {
  case str@PathList("admin", "bower_components", remains@_*) => remains match {
    case Seq("angular", "angular.min.js") => MergeStrategy.deduplicate
    case Seq("angular-route", "angular-route.min.js") => MergeStrategy.deduplicate
    case Seq("ng-file-upload", "ng-file-upload.min.js") => MergeStrategy.deduplicate
    case Seq("underscore", "underscore-min.js") => MergeStrategy.deduplicate
    case Seq("jquery", "dist", "jquery.min.js") => MergeStrategy.deduplicate
    case Seq("semantic-ui", "dist", "semantic.min.css") => MergeStrategy.deduplicate
    case Seq("semantic-ui", "dist", "semantic.min.js") => MergeStrategy.deduplicate
    case Seq("semantic-ui", "dist", "themes", "default", "assets", _*) => MergeStrategy.deduplicate
    case _ => MergeStrategy.discard
  }
  case x =>
    (assemblyMergeStrategy in assembly).value.apply(x)
}

mainClass in assembly := Some("com.gtan.repox.Main")

test in assembly := {}
