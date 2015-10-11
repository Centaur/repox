name := "repox"

organization := "com.gtan"

scalaVersion := "2.11.7"

val akkaVersion = "2.4.0"

libraryDependencies ++= {
  val undertowVer = "1.3.0.Final"
  val logbackVer = "1.1.3"
  val leveldbVer = "0.7"
  val leveldbjniVer = "1.8"
  val scalaTestVer = "2.2.5"
  val playJsonVer = "2.4.3"
  val scalaLoggingVer = "3.1.0"
  val ningVer = "1.8.16"
  val protobufVer = "2.6.1"
  val guavaVer = "18.0"
  Seq(
    "io.undertow" % "undertow-core" % undertowVer,
    ("com.ning" % "async-http-client" % ningVer)
      .exclude("org.slf4j", "slf4j-api"),
    ("com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVer)
      .exclude("org.scala-lang", "scala-library"),
    ("ch.qos.logback" % "logback-classic" % logbackVer)
      .exclude("org.slf4j", "slf4j-api"),
    ("com.typesafe.akka" %% "akka-actor" % akkaVersion)
      .exclude("org.slf4j", "slf4j-api"),
    ("com.typesafe.akka" %% "akka-slf4j" % akkaVersion)
      .exclude("org.slf4j", "slf4j-api"),
    ("com.typesafe.akka" %% "akka-agent" % akkaVersion)
      .exclude("org.scala-lang", "scala-library"),
    ("com.typesafe.akka" %% "akka-persistence" % akkaVersion)
      .exclude("org.scala-lang", "scala-library"),
    "org.iq80.leveldb" % "leveldb" % leveldbVer,
    "org.fusesource.leveldbjni" % "leveldbjni-all" % leveldbjniVer,
    "com.typesafe.akka" %% "akka-persistence-query-experimental" % akkaVersion,
    ("com.typesafe.play" %% "play-json" % playJsonVer)
      .exclude("org.scala-lang", "scala-library"),
    "com.google.protobuf" % "protobuf-java" % protobufVer,
    "com.google.guava" % "guava" % guavaVer,
    "org.scalatest" %% "scalatest" % scalaTestVer % "test"
  )
}

// to use repox, this option must be false, or when retry updateClassifiers, it will not download srcs
//updateOptions := updateOptions.value.withCachedResolution(false)

transitiveClassifiers := Seq("sources")

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
    case Seq("angular", "angular.js") => MergeStrategy.deduplicate
    case Seq("angular-route", "angular-route.js") => MergeStrategy.deduplicate
    case Seq("underscore", "underscore-min.js") => MergeStrategy.deduplicate
    case Seq("jquery", "dist", "jquery.min.js") => MergeStrategy.deduplicate
    case Seq("semantic-ui", "dist", "semantic.min.css") => MergeStrategy.deduplicate
    case Seq("semantic-ui", "dist", "semantic.min.js") => MergeStrategy.deduplicate
    case Seq("semantic-ui", "dist", "themes", "default", "assets", all@_*) => MergeStrategy.deduplicate
    case _ => MergeStrategy.discard
  }
  case x =>
    (assemblyMergeStrategy in assembly).value.apply(x)
}
