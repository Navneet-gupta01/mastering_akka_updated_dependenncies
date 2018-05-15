import Dependencies._

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.example",
      scalaVersion := "2.12.6",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "queing-sample",
    libraryDependencies ++= Seq(
    	"org.scalatest" %% "scalatest" % "3.0.1" % "test",
	   	"com.typesafe.akka" %% "akka-actor" % "2.5.12",
	   	"com.typesafe.akka" %% "akka-testkit" % "2.5.12" % Test,
	   	"com.typesafe.akka" %% "akka-slf4j" % "2.5.12",
	   	"ch.qos.logback" % "logback-classic" % "1.2.3",
    )
  )
