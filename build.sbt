import org.scalajs.sbtplugin.cross.CrossProject
import sbt.Keys._
import LDMLTasks._

val cldrVersion = settingKey[String]("The version of CLDR used.")
lazy val downloadFromZip: TaskKey[Unit] =
  taskKey[Unit]("Download the sbt zip and extract it")

val commonSettings: Seq[Setting[_]] = Seq(
  version := "0.1.0-SNAPSHOT",
  organization := "com.github.cquiroz",
  scalaVersion := "2.11.8",
  crossScalaVersions := Seq("2.10.4", "2.11.8"),
  scalacOptions ++= Seq("-deprecation", "-feature", "-Xfatal-warnings"),
  mappings in (Compile, packageBin) ~= {
    // Exclude CLDR files...
    _.filter(!_._2.contains("core"))
  },
  exportJars := true,

  publishMavenStyle := true,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  pomExtra :=
      <developers>
        <developer>
          <id>cquiroz</id>
          <name>Carlos Quiroz</name>
          <url>https://github.com/cquiroz/</url>
        </developer>
      </developers>
  ,
  pomIncludeRepository := { _ => false }
)

lazy val root: Project = project.in(file("."))
  .settings(commonSettings)
  .settings(
    name := "scala-locales",
    publish := {},
    publishLocal := {}
  )
  .aggregate(coreJS, coreJVM, testSuiteJS, testSuiteJVM)

lazy val core: CrossProject = crossProject.crossType(CrossType.Pure).
  settings(commonSettings: _*).
  settings(
    name := "scala-locales",
    cldrVersion := "29",
    downloadFromZip := {
      val xmlFiles = ((resourceDirectory in Compile) / "core").value
      if (java.nio.file.Files.notExists(xmlFiles.toPath)) {
        println("CLDR files missing, downloading...")
        IO.unzipURL(
          new URL(s"http://unicode.org/Public/cldr/${cldrVersion.value}/core.zip"),
          xmlFiles)
      } else {
        println("CLDR files already available")
      }
    },
    compile in Compile <<= (compile in Compile).dependsOn(downloadFromZip),
    sourceGenerators in Compile += Def.task {
      generateLocaleData((sourceManaged in Compile).value,
        ((resourceDirectory in Compile) / "core").value)
    }.taskValue
  ).
  jsConfigure(_.enablePlugins(ScalaJSJUnitPlugin))

lazy val coreJS: Project = core.js
lazy val coreJVM: Project = core.jvm

lazy val testSuite: CrossProject = CrossProject(
  jvmId = "testSuiteJVM",
  jsId = "testSuite",
  base = file("testSuite"),
  crossType = CrossType.Full).
  jsConfigure(_.enablePlugins(ScalaJSJUnitPlugin)).
  settings(commonSettings: _*).
  settings(
    publish := {},
    publishLocal := {},
    testOptions +=
      Tests.Argument(TestFramework("com.novocode.junit.JUnitFramework"),
        "-v", "-a")
  ).
  jsSettings(
    name := "scala locale testSuite on JS",
    scalaJSUseRhino := false
  ).
  jsConfigure(_.dependsOn(coreJS)).
  jvmSettings(
    name := "scala locale testSuite on JVM",
    libraryDependencies +=
      "com.novocode" % "junit-interface" % "0.9" % "test"
  ).
  jvmConfigure(_.dependsOn(coreJVM))

lazy val testSuiteJS: Project = testSuite.js
lazy val testSuiteJVM: Project = testSuite.jvm
