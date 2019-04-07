lazy val root = (project in file(".")).enablePlugins(PlayScala)

name := "sf-cors-proxy"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  cache,
  ws,
  filters,
  "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % Test
)
