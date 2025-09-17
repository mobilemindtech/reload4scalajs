addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.20.1")

lazy val liveReloadPlugin = RootProject(file("../.."))

lazy val root = project
	.in(file("."))
	.dependsOn(liveReloadPlugin)
