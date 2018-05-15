name := "order-services"

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.0.1" % "test",
  "org.mockito" % "mockito-all" % "2.0.2-beta"  % "test",
  "com.typesafe.akka" %% "akka-testkit" % "2.5.12" % Test,
  "junit" % "junit" % "4.12"  % "test"  
)