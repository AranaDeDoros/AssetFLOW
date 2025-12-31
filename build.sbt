import Dependencies.*

import scala.collection.Seq

ThisBuild / scalaVersion     := "2.13.16"
ThisBuild / version          := "0.8.0"
ThisBuild / organization     := "com.aranadedoros"
ThisBuild / organizationName := "aranadedoros"
Compile / doc / scalacOptions ++= Seq(
  "-skip-packages", "example"
)

lazy val scrimageVersion = "4.0.31"
resolvers += Resolver.mavenCentral

lazy val root = (project in file("."))
  .settings(
    name := "asset-flow",

    libraryDependencies ++= Seq(
      munit % Test,
      "com.sksamuel.scrimage" % "scrimage-core" % scrimageVersion,
      "com.sksamuel.scrimage" %% "scrimage-scala" % scrimageVersion,
      "com.sksamuel.scrimage" % "scrimage-webp" % scrimageVersion,
      "com.sksamuel.scrimage" % "scrimage-filters" % scrimageVersion,
      "de.androidpit" % "color-thief" % "1.1.2",
       "org.scala-lang" % "scala-reflect" % scalaVersion.value
    )
  )

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
