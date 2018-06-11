name := "common_ch07"

libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-actor" % "2.5.12",
        "com.typesafe.akka" %% "akka-testkit" % "2.5.12" % Test,
        "com.typesafe.akka" %% "akka-slf4j" % "2.5.12",
        "ch.qos.logback" % "logback-classic" % "1.2.3",
		"com.typesafe.akka" %% "akka-persistence-query" % "2.5.12",
		"com.typesafe.akka" %% "akka-persistence" % "2.5.12",
        "com.typesafe.akka" %% "akka-persistence-cassandra" % "0.84",
  		"com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % "0.84" % Test,
        "com.google.protobuf" % "protobuf-java" % "3.5.1",
        "com.typesafe.akka" %% "akka-stream" % "2.5.12",
        "com.typesafe.akka" %% "akka-http"   % "10.1.1",
        "org.json4s" %% "json4s-ext" % "3.6.0-M3",
        "org.json4s" %% "json4s-native" % "3.6.0-M3",
        "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.1",
		"com.typesafe.akka" %% "akka-http-testkit" % "10.1.1"
)