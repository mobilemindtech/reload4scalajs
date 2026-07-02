

name := "example"
scalaVersion := "3.8.4"

lazy val app = (project in file("."))
  .enablePlugins(ScalaJSPlugin, PageReload4sPlugin)
  .settings(
    name := "example",
    scalaJSUseMainModuleInitializer := true,
    pagereload := ReloadConfig(
      pathWatchToReload = baseDirectory.value / "public",
      publicFolder = baseDirectory.value / "public",
      copyJsToPath = baseDirectory.value / "public" / "assets" / "js",
      debug = true
    ),
    libraryDependencies ++= Seq(
      "org.scala-js" %% "scalajs-dom" % "2.8.1"
    )
  )