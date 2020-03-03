ThisBuild / name := "poor-mans-runtime"
ThisBuild / version := "0.1.0"
ThisBuild / scalaVersion := "2.13.1"

val zioVersion = "1.0.0-RC18"

lazy val `runtime` =
  (project in file("."))
    .settings(
      libraryDependencies := Seq(
        "dev.zio" %% "zio" % zioVersion,
        "dev.zio" %% "zio-test" % zioVersion % Test,
        "com.lihaoyi" %% "fansi" % "0.2.9"
      ),
      scalacOptions ++= Seq(
        "-deprecation",
        "-Xfatal-warnings",
        "-encoding",
        "utf8",
        "-deprecation",
        "-unchecked",
        "-Xlint:_",
        "-feature",
        "-Yrangepos",
        "-Ywarn-value-discard",
        "-Ywarn-dead-code",
        "-Ywarn-unused:_"
      )
    )
