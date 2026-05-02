import com.peknight.build.gav.*
import com.peknight.build.sbt.*

commonSettings

lazy val qlexpress = (project in file("."))
  .settings(name := "qlexpress")
  .aggregate(
    qlexpress3Demo.jvm,
    qlexpress3Demo.js,
  )

lazy val qlexpress3Demo = (crossProject(JVMPlatform, JSPlatform) in file("qlexpress3-demo"))
  .settings(name := "qlexpress3-demo")
  .settings(crossTestDependencies(
    peknight.validation,
    scalaTest.flatSpec,
  ))
  .jvmSettings(
    javaOptions ++= Seq(
      "--add-opens=java.base/java.util=ALL-UNNAMED"
    ),
    libraryDependencies ++= Seq(
      jvmDependency(alibaba.qlExpress) exclude("commons-logging", "commons-logging"),
      jvmDependency(spring.context),
    ),
  )
