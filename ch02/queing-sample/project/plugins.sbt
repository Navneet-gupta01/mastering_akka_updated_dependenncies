resolvers += "sonatype-releases" at "https://oss.sonatype.org/content/repositories/releases/"

// to format scala source code
addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.2")

// enable updating file headers eg. for copyright
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.0.0")
addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "5.2.4")
