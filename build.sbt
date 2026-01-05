
lazy val scala212 = "2.12.20"
lazy val scala213 = "2.13.15"
lazy val scala3 = "3.6.2"
lazy val scalaVersions = List(scala212, scala213, scala3)

ThisBuild / crossScalaVersions := scalaVersions
ThisBuild / scalaVersion     := scala212
ThisBuild / version          := "0.2.10"
ThisBuild / organization     := "br.com.mobilemind"
ThisBuild / organizationName := "Mobild Mind"
ThisBuild / organizationHomepage := Some(url("https://mobilemind.com.br"))
ThisBuild /description := "ScalaJS live reload"
ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"


lazy val root = (project in file("."))
  .settings(
    name := "livereload",
    sbtPlugin := true,
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "cask" % "0.10.2",
      "com.lihaoyi" %% "upickle" % "4.0.2",
    ),
    credentials ++= Seq(
      Credentials(Path.userHome / ".sbt" / "sonatype_credentials"),
      Credentials(Path.userHome / ".sbt" / "sonatype_gpg")
    )
)
  .settings(addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.20.2"))
