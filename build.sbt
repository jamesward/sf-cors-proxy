name := "sf-cors-proxy"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.1"

libraryDependencies ++= Seq(cache, ws)

lazy val root = (project in file(".")).enablePlugins(PlayScala)