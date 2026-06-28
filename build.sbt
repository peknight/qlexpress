import com.peknight.build.gav
import com.peknight.build.gav.*
import com.peknight.build.sbt.*

commonSettings

lazy val qlexpress = (project in file("."))
  .settings(name := "qlexpress")
  .aggregate(qlexpress3Demo.projectRefs *)

lazy val qlexpress3Demo = (projectMatrix in file("qlexpress3-demo"))
  .settings(name := "qlexpress3-demo")
  .settings(libraryDependencies ++= testDependencies(
    peknight.validation,
    scalaTest.flatSpec,
  ))
  .jvmPlatform(
    scalaVersions = Seq(scala.scala3.version),
    settings = Seq(
      javaOptions ++= Seq(
        "--add-opens=java.base/java.util=ALL-UNNAMED"
      ),
      libraryDependencies ++= Seq(
        jvmDependency(alibaba.qlExpress) exclude("commons-logging", "commons-logging"),
        jvmDependency(spring.context),
      ),
    )
  )
  .jsPlatform(scalaVersions = Seq(scala.scala3.version))
