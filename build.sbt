import AssemblyKeys._

name := "finagle_study"

scalaVersion := "2.9.2"

sbtVersion := "0.12"

mainClass := Some("com.ccc.tparty.proto.Main")

resolvers += "twitter-repo" at "http://maven.twttr.com"

libraryDependencies ++= Seq(
  "com.twitter" % "finagle-core_2.9.1" % "4.0.2",
  "com.twitter" % "finagle-http_2.9.1" % "4.0.2",
  "com.twitter" % "finagle-stream_2.9.1" % "4.0.2",
  "com.google.code.guice" % "guice" % "1.0"
)

EclipseKeys.withSource := true

assemblySettings