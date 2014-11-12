name := "AkkaAlgebird"

version := "0.0.1"

scalaVersion := "2.11.2"

scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-feature")

site.settings

site.includeScaladoc()

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq("org.scalatest" % "scalatest_2.11" % "2.2.1" % "test",
    "junit" % "junit" % "4.11" % "test",
    "org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided",
    "com.typesafe.akka" %% "akka-actor" % "2.3.6",
    "com.typesafe.akka" %% "akka-slf4j" % "2.3.6",
    "com.typesafe.akka" %% "akka-remote" % "2.3.6",
    "com.typesafe.akka" %% "akka-cluster" % "2.3.6",
    "com.typesafe.akka" %% "akka-agent" % "2.3.6", 
    "com.typesafe.akka" %% "akka-testkit" % "2.3.6"% "test",
    "com.typesafe.akka" %% "akka-contrib" % "2.3.6",
    "com.typesafe.akka" %% "akka-persistence-experimental" % "2.3.6",
    "com.twitter" % "algebird_2.11" % "0.8.1",
    "com.twitter" % "algebird-core_2.11" % "0.8.1",
    "com.twitter" % "algebird-test_2.11" % "0.8.1",
    "commons-io" % "commons-io" % "2.4" % "test"
)