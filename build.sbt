name := "sf-cors-proxy"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.1"

libraryDependencies ++= Seq(
  cache,
  ws,
  filters,
  "org.scalatestplus" %% "play" % "1.1.0" % "test"
)

lazy val root = (project in file(".")).enablePlugins(PlayScala)