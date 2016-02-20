import java.io.FileOutputStream

import com.typesafe.tools.mima.plugin.MimaPlugin.mimaDefaultSettings
import com.typesafe.tools.mima.plugin.MimaKeys
import MimaKeys.{ previousArtifacts, binaryIssueFilters }

val binaryCompatibilityVersion = "1.0.0-M7"

lazy val releaseSettings = Seq(
  publishMavenStyle := true,
  licenses := Seq("Apache 2.0" -> url("http://opensource.org/licenses/Apache-2.0")),
  homepage := Some(url("https://github.com/alexarchambault/coursier")),
  pomExtra := {
    <scm>
      <connection>scm:git:github.com/alexarchambault/coursier.git</connection>
      <developerConnection>scm:git:git@github.com:alexarchambault/coursier.git</developerConnection>
      <url>github.com/alexarchambault/coursier.git</url>
    </scm>
    <developers>
      <developer>
        <id>alexarchambault</id>
        <name>Alexandre Archambault</name>
        <url>https://github.com/alexarchambault</url>
      </developer>
    </developers>
  },
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
  credentials += {
    Seq("SONATYPE_USER", "SONATYPE_PASS").map(sys.env.get) match {
      case Seq(Some(user), Some(pass)) =>
        Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", user, pass)
      case _ =>
        Credentials(Path.userHome / ".ivy2" / ".credentials")
    }
  }
)

lazy val noPublishSettings = Seq(
  publish := (),
  publishLocal := (),
  publishArtifact := false
)

lazy val noPublish211Settings = Seq(
  publish := {
    if (scalaVersion.value startsWith "2.10.")
      publish.value
    else
      ()
  },
  publishLocal := {
    if (scalaVersion.value startsWith "2.10.")
      publishLocal.value
    else
      ()
  },
  publishArtifact := {
    if (scalaVersion.value startsWith "2.10.")
      publishArtifact.value
    else
      false
  }
)

lazy val noPublish210Settings = Seq(
  publish := {
    if (scalaVersion.value startsWith "2.10.")
      ()
    else
      publish.value
  },
  publishLocal := {
    if (scalaVersion.value startsWith "2.10.")
      ()
    else
      publishLocal.value
  },
  publishArtifact := {
    if (scalaVersion.value startsWith "2.10.")
      false
    else
      publishArtifact.value
  }
)

lazy val baseCommonSettings = Seq(
  organization := "com.github.alexarchambault",
  resolvers ++= Seq(
    "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases",
    Resolver.sonatypeRepo("releases")
  ),
  scalacOptions += "-target:jvm-1.6",
  javacOptions ++= Seq(
    "-source", "1.6",
    "-target", "1.6"
  ),
  javacOptions in Keys.doc := Seq()
) ++ releaseSettings

lazy val commonSettings = baseCommonSettings ++ Seq(
  scalaVersion := "2.11.7",
  crossScalaVersions := Seq("2.11.7", "2.10.6"),
  libraryDependencies ++= {
    if (scalaVersion.value startsWith "2.10.")
      Seq(compilerPlugin("org.scalamacros" % "paradise" % "2.0.1" cross CrossVersion.full))
    else
      Seq()
  }
)

lazy val core = crossProject
  .settings(commonSettings: _*)
  .settings(mimaDefaultSettings: _*)
  .settings(
    name := "coursier-java-6",
    resourceGenerators.in(Compile) += {
      (target, version).map { (dir, ver) =>
        import sys.process._

        val f = dir / "coursier.properties"
        dir.mkdirs()

        val p = new java.util.Properties()

        p.setProperty("version", ver)
        p.setProperty("commit-hash", Seq("git", "rev-parse", "HEAD").!!.trim)

        val w = new FileOutputStream(f)
        p.store(w, "Coursier properties")
        w.close()

        println(s"Wrote $f")

        Seq(f)
      }.taskValue
    },
    previousArtifacts := Set.empty, //Set(organization.value %% moduleName.value % binaryCompatibilityVersion),
    binaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._
      import com.typesafe.tools.mima.core.ProblemFilters._
      
      Seq()
    }
  )
  .jvmSettings(
    libraryDependencies ++=
      Seq(
        "org.scalaz" %% "scalaz-core" % "7.1.2"
      ) ++ {
        if (scalaVersion.value.startsWith("2.10.")) Seq()
        else Seq(
          "org.scala-lang.modules" %% "scala-xml" % "1.0.3"
        )
      }
  )
  .jsSettings(
    libraryDependencies ++= Seq(
      "com.github.japgolly.fork.scalaz" %%% "scalaz-core" % (if (scalaVersion.value.startsWith("2.10.")) "7.1.1" else "7.1.2"),
      "org.scala-js" %%% "scalajs-dom" % "0.8.0",
      "be.doeraene" %%% "scalajs-jquery" % "0.8.0"
    )
  )

lazy val coreJvm = core.jvm
lazy val coreJs = core.js

lazy val `fetch-js` = project
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(coreJs)
  .settings(commonSettings)
  .settings(noPublishSettings)
  .settings(
    name := "coursier-fetch-js"
  )

lazy val tests = crossProject
  .dependsOn(core)
  .settings(commonSettings: _*)
  .settings(noPublishSettings: _*)
  .settings(
    name := "coursier-tests-java-6",
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %% "scala-async" % "0.9.1" % "provided",
      "com.lihaoyi" %%% "utest" % "0.3.0" % "test"
    ),
    unmanagedResourceDirectories in Test += (baseDirectory in LocalRootProject).value / "tests" / "shared" / "src" / "test" / "resources",
    testFrameworks += new TestFramework("utest.runner.Framework")
  )
  .jsSettings(
    postLinkJSEnv := NodeJSEnv().value,
    scalaJSStage in Global := FastOptStage
  )

lazy val testsJvm = tests.jvm.dependsOn(cache % "test")
lazy val testsJs = tests.js.dependsOn(`fetch-js` % "test")

lazy val cache = project
  .dependsOn(coreJvm)
  .settings(commonSettings)
  .settings(mimaDefaultSettings)
  .settings(
    name := "coursier-cache-java-6",
    libraryDependencies ++= Seq(
      "org.scalaz" %% "scalaz-concurrent" % "7.1.2",
      "com.lihaoyi" %% "ammonite-terminal" % "0.5.0",
      "com.google.guava" % "guava" % "19.0"
    ),
    previousArtifacts := Set.empty, //Set(organization.value %% moduleName.value % binaryCompatibilityVersion),
    binaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._
      import com.typesafe.tools.mima.core.ProblemFilters._
      
      Seq(
        ProblemFilters.exclude[MissingTypesProblem]("coursier.TermDisplay$Info$"),
        ProblemFilters.exclude[MissingMethodProblem]("coursier.TermDisplay#Info.apply"),
        ProblemFilters.exclude[MissingMethodProblem]("coursier.TermDisplay#Info.copy"),
        ProblemFilters.exclude[MissingMethodProblem]("coursier.TermDisplay#Info.pct"),
        ProblemFilters.exclude[MissingMethodProblem]("coursier.TermDisplay#Info.this")
      )
    }
  )

lazy val bootstrap = project
  .settings(baseCommonSettings)
  .settings(noPublishSettings)
  .settings(
    name := "coursier-bootstrap-java-6",
    artifactName := {
      val artifactName0 = artifactName.value
      (sv, m, artifact) =>
        if (artifact.`type` == "jar" && artifact.extension == "jar")
          "bootstrap.jar"
        else
          artifactName0(sv, m, artifact)
    },
    crossPaths := false,
    autoScalaLibrary := false
  )

lazy val cli = project
  .dependsOn(coreJvm, cache)
  .settings(commonSettings)
  .settings(noPublish210Settings)
  .settings(packAutoSettings)
  .settings(proguardSettings)
  .settings(
    name := "coursier-cli-java-6",
    libraryDependencies ++= {
      if (scalaVersion.value startsWith "2.10.")
        Seq()
      else
        Seq("com.github.alexarchambault" %% "case-app" % "1.0.0-M4")
    },
    resourceGenerators in Compile += packageBin.in(bootstrap).in(Compile).map { jar =>
      Seq(jar)
    }.taskValue,
    ProguardKeys.proguardVersion in Proguard := "5.2.1",
    ProguardKeys.options in Proguard ++= Seq(
      "-dontwarn",
      "-keep class coursier.cli.Coursier {\n  public static void main(java.lang.String[]);\n}",
      "-keep class coursier.cli.IsolatedClassLoader {\n  public java.lang.String[] getIsolationTargets();\n}"
    ),
    javaOptions in (Proguard, ProguardKeys.proguard) := Seq("-Xmx3172M"),
    artifactPath in Proguard := (ProguardKeys.proguardDirectory in Proguard).value / "coursier-standalone.jar",
    artifacts ++= {
      if (scalaBinaryVersion.value == "2.10")
        Nil
      else Seq(
        Artifact(
          moduleName.value,
          "jar",
          "jar",
          "standalone"
        )
      )
    },
    packagedArtifacts <++= {
      (
        moduleName,
        scalaBinaryVersion,
        ProguardKeys.proguard in Proguard
      ).map {
        (mod, sbv, files) =>
          if (sbv == "2.10")
            Map.empty[Artifact, File]
          else {
            val f = files match {
              case Seq(f) => f
              case Seq() =>
                throw new Exception("Found no proguarded files. Expected one.")
              case _ =>
                throw new Exception("Found several proguarded files. Don't know how to publish all of them.")
            }

            Map(
              // FIXME Same Artifact as above
              Artifact(
                mod,
                "jar",
                "jar",
                "standalone"
              ) -> f
            )
          }
      }
    }
  )

lazy val web = project
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(coreJs, `fetch-js`)
  .settings(commonSettings)
  .settings(noPublishSettings)
  .settings(
    libraryDependencies ++= {
      if (scalaVersion.value startsWith "2.10.")
        Seq()
      else
        Seq("com.github.japgolly.scalajs-react" %%% "core" % "0.9.0")
    },
    sourceDirectory := {
      val dir = sourceDirectory.value

      if (scalaVersion.value startsWith "2.10.")
        dir / "dummy"
      else
        dir
    },
    test in Test := (),
    testOnly in Test := (),
    resolvers += "Webjars Bintray" at "https://dl.bintray.com/webjars/maven/",
    jsDependencies ++= Seq(
      ("org.webjars.bower" % "bootstrap" % "3.3.4" intransitive()) / "bootstrap.min.js" commonJSName "Bootstrap",
      ("org.webjars.bower" % "react" % "0.12.2" intransitive()) / "react-with-addons.js" commonJSName "React",
      ("org.webjars.bower" % "bootstrap-treeview" % "1.2.0" intransitive()) / "bootstrap-treeview.min.js" commonJSName "Treeview",
      ("org.webjars.bower" % "raphael" % "2.1.4" intransitive()) / "raphael-min.js" commonJSName "Raphael"
    )
  )

lazy val doc = project
  .dependsOn(coreJvm, cache)
  .settings(commonSettings)
  .settings(noPublishSettings)
  .settings(tutSettings)
  .settings(
    tutSourceDirectory := baseDirectory.value,
    tutTargetDirectory := baseDirectory.value / ".."
  )

// Don't try to compile that if you're not in 2.10
lazy val plugin = project
  .dependsOn(coreJvm, cache)
  .settings(baseCommonSettings)
  .settings(noPublish211Settings)
  .settings(
    name := "coursier-sbt-plugin-java-6",
    sbtPlugin := {
      scalaVersion.value.startsWith("2.10.")
    },
    libraryDependencies += "com.google.guava" % "guava" % "19.0",
    // added so that 2.10 artifacts of the other modules can be found by
    // the too-naive-for-now inter-project resolver of the coursier SBT plugin
    resolvers += Resolver.sonatypeRepo("snapshots")
  )

lazy val `coursier` = project.in(file("."))
  .aggregate(coreJvm, coreJs, `fetch-js`, testsJvm, testsJs, cache, bootstrap, cli, plugin, web, doc)
  .settings(commonSettings)
  .settings(noPublishSettings)
  .settings(releaseSettings)
