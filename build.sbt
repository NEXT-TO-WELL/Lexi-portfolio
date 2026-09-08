name := """Lexi"""
organization := "com.lexi"
version := "1.0-SNAPSHOT"

import sbtcrossproject.CrossPlugin.autoImport.{CrossType, JVMPlatform, crossProject}
import scalajscrossproject.ScalaJSCrossPlugin.autoImport.JSPlatform
import play.sbt.PlayImport.PlayKeys.includeDocumentationInBinary

val scala3Version = "3.3.7"
val jacksonVersion = "2.18.10"

ThisBuild / semanticdbEnabled := true
ThisBuild / scalacOptions ++= Seq(
  "-Wunused:imports",
  "-Wconf:src=.*/target/scala-[^/]+/(routes|twirl)/.*:silent"
)
ThisBuild / Compile / packageDoc / publishArtifact := false
ThisBuild / dependencyOverrides ++= Seq(
  "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-cbor" % jacksonVersion,
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % jacksonVersion,
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % jacksonVersion,
  "com.fasterxml.jackson.module" % "jackson-module-parameter-names" % jacksonVersion,
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion
)

lazy val commonSettings = Seq(
  scalaVersion := scala3Version,
  libraryDependencies ++= Seq(
    "org.scala-js" %%% "scalajs-dom" % "2.8.1",
    "com.raquo" %%% "laminar" % "17.2.1",
    "com.raquo" %%% "waypoint" % "10.0.0-M1",
    "com.lihaoyi" %%% "upickle" % "4.4.2",
    "org.apache.pekko" %% "pekko-stream" % "1.4.0"
  )
)

lazy val shared = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("shared"))
  .settings(
    scalaVersion := scala3Version,
    libraryDependencies += "com.lihaoyi" %%% "upickle" % "4.4.2"
  )

lazy val root = (project in file("."))
  .dependsOn(shared.jvm)
  .enablePlugins(PlayScala)
  .settings(
    scalaVersion := scala3Version,
    maintainer := "NEXT-TO-WELL",
    includeDocumentationInBinary := false,
    libraryDependencies += guice,

    // Include static assets in the distribution.
    Universal / mappings ++= {
      val publicDir = (baseDirectory.value / "public")
      (publicDir ** "*").get.filterNot(_.isDirectory).map { file =>
        file -> s"public/${publicDir.toPath.relativize(file.toPath)}"
      }
    },

    // Slick, MySQL, authentication, and test dependencies.
    libraryDependencies ++= Seq(
      "org.playframework" %% "play-slick" % "6.2.0",
      "org.playframework" %% "play-slick-evolutions" % "6.2.0",
      "com.mysql" % "mysql-connector-j" % "26.7.0",
      "com.lihaoyi" %%% "upickle" % "4.4.2",
      "com.github.jwt-scala" %% "jwt-play" % "11.0.3",
      "nl.martijndwars" % "web-push" % "5.1.2",
      "org.bouncycastle" % "bcprov-jdk18on" % "1.85.2",
      "org.mindrot" % "jbcrypt" % "0.4"
    ),

    libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.2" % Test
  )

lazy val frontend = (project in file("frontend/general"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(shared.js)
  .settings(
    commonSettings,
    libraryDependencies += "org.scalatest" %%% "scalatest" % "3.2.19" % Test,
    scalaJSUseMainModuleInitializer := true,
    // Development bundle.
    Compile / fastLinkJS / scalaJSLinkerOutputDirectory := (root / baseDirectory).value / "public" / "javascripts" / "main",

    // Production bundle.
    Compile / fullLinkJS / scalaJSLinkerOutputDirectory := (root / baseDirectory).value / "public" / "javascripts" / "main",

    // Avoid Closure Compiler issues in fullLinkJS.
    Compile / fullLinkJS / scalaJSLinkerConfig ~= { _.withClosureCompiler(false) },
    scalaJSStage in Global := FastOptStage
  )

lazy val auth = (project in file("frontend/auth"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    commonSettings,
    scalaJSUseMainModuleInitializer := true,
    // Authentication bundle.
    Compile / fastLinkJS / scalaJSLinkerOutputDirectory := (root / baseDirectory).value / "public" / "javascripts" / "auth",
    Compile / fullLinkJS / scalaJSLinkerOutputDirectory := (root / baseDirectory).value / "public" / "javascripts" / "auth",

    // Avoid Closure Compiler issues in fullLinkJS.
    Compile / fullLinkJS / scalaJSLinkerConfig ~= { _.withClosureCompiler(false) }
  )
  .dependsOn(frontend)

lazy val manager = (project in file("frontend/manager"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    commonSettings,
    scalaJSUseMainModuleInitializer := true,
    // Manager bundle.
    Compile / fastLinkJS / scalaJSLinkerOutputDirectory := (root / baseDirectory).value / "public" / "javascripts" / "manager",
    Compile / fullLinkJS / scalaJSLinkerOutputDirectory := (root / baseDirectory).value / "public" / "javascripts" / "manager",

    // Avoid Closure Compiler issues in fullLinkJS.
    Compile / fullLinkJS / scalaJSLinkerConfig ~= { _.withClosureCompiler(false) }
  )
  .dependsOn(frontend)
