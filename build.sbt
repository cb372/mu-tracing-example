import higherkindness.mu.rpc.srcgen.Model._

inThisBuild(Seq(
  organization := "com.example",
  scalaVersion := "2.13.1",
  scalacOptions += "-language:higherKinds",
  addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.0" cross CrossVersion.full)
))

def isOldScala(sv: String): Boolean =
  CrossVersion.partialVersion(sv) match {
    case Some((2, minor)) if minor < 13 => true
    case _                              => false
  }

val macroSettings: Seq[Setting[_]] = {

  def paradiseDependency(sv: String): Seq[ModuleID] =
    if (isOldScala(sv))
      Seq(compilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.patch))
    else
      Seq.empty

  def macroAnnotationScalacOption(sv: String): Seq[String] =
    if (isOldScala(sv))
      Seq.empty
    else
      Seq("-Ymacro-annotations")

  Seq(
    libraryDependencies ++= paradiseDependency(scalaVersion.value),
    scalacOptions ++= macroAnnotationScalacOption(scalaVersion.value)
  )
}

val muVersion = "0.22.0"

val protocol = project
  .settings(
    name := "mu-tracing-example-protocol",

    libraryDependencies ++= Seq(
      // Needed for the generated code to compile
      "io.higherkindness" %% "mu-rpc-service" % muVersion,
      "io.higherkindness" %% "mu-rpc-fs2" % muVersion
    ),

    // Needed to expand the @service macro annotation
    macroSettings,

    // Generate sources from .proto files
    muSrcGenIdlType := IdlType.Proto,
    // Make it easy for 3rd-party clients to communicate with us via gRPC
    muSrcGenIdiomaticEndpoints := true,
  )

val client = project
  .settings(
    name := "mu-tracing-example-rpc-client",

    libraryDependencies ++= Seq(
      "io.higherkindness" %% "mu-rpc-client-netty" % muVersion,
      "dev.profunktor" %% "console4cats" % "0.8.1",
      "org.tpolecat" %% "natchez-jaeger" % "0.0.11",
      "org.slf4j" % "slf4j-simple" % "1.7.30"
    )
  )
  .dependsOn(protocol)

val serverA = project
  .settings(
    name := "mu-tracing-example-rpc-serverA",

    libraryDependencies ++= Seq(
      "io.higherkindness" %% "mu-rpc-server" % muVersion,
      "io.higherkindness" %% "mu-rpc-client-netty" % muVersion,
      "org.tpolecat" %% "natchez-jaeger" % "0.0.11",
      "org.slf4j" % "slf4j-simple" % "1.7.30"
    ).map(_.exclude("org.slf4j", "slf4j-jdk14")),

    // Start the server in a separate process so it shuts down cleanly when you hit Ctrl-C
    fork := true
  )
  .dependsOn(protocol)

val serverB = project
  .settings(
    name := "mu-tracing-example-rpc-serverB",

    libraryDependencies ++= Seq(
      "io.higherkindness" %% "mu-rpc-server" % muVersion,
      "org.tpolecat" %% "natchez-jaeger" % "0.0.11",
      "org.slf4j" % "slf4j-simple" % "1.7.30"
    ).map(_.exclude("org.slf4j", "slf4j-jdk14")),

    // Start the server in a separate process so it shuts down cleanly when you hit Ctrl-C
    fork := true
  )
  .dependsOn(protocol)

val root = (project in file("."))
  .settings(
    name := "mu-tracing-example"
  )
  .aggregate(protocol, serverA, serverB, client)
