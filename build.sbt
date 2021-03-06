val nova_version      = "1.0"
val scala_version     = "2.12.5"
val paradise_version  = "2.1.0"
val scalatestVersion  = "3.0.5"

name := "nova"
organization := "edu.stanford.ppl"

val common = Seq(
  scalaVersion := scala_version,
  version := nova_version,

  /** External Libraries (e.g. maven dependencies) **/
  libraryDependencies ++= Seq(
    "org.scala-lang" % "scala-reflect" % scala_version,  // Reflection
    "org.scalatest" %% "scalatest" % scalatestVersion,
    "com.github.scopt" %% "scopt" % "3.7.0",

    // Alternative library for testing
    //"com.lihaoyi" %% "utest" % "0.6.3" % "test",         // Testing

    // These are a bit bulky, leaving them out in favor of a stripped down version for now
    //"org.apache.commons" % "commons-lang3" % "3.3.2",
    //"commons-io" % "commons-io" % "2.5"
  ),

  /** Scalac Options **/
  scalacOptions += "-target:jvm-1.8",               // JVM 1.8
  scalacOptions ++= Seq("-encoding", "UTF-8"),      // Encoding using UTF-8
  scalacOptions += "-unchecked",                    // Enable additional warnings
  scalacOptions += "-deprecation",                  // Enable warnings on deprecated usage
  scalacOptions += "-feature",                      // Warnings for features requiring explicit import
  scalacOptions += "-Xfatal-warnings",              // Warnings are errors
  scalacOptions += "-language:higherKinds",         // Globally enable higher kinded type parameters
  scalacOptions += "-language:implicitConversions", // Globally enable implicit conversions
  scalacOptions += "-language:experimental.macros", // Globally enable macros
  scalacOptions += "-language:existentials",        // Globally enable existentials
  scalacOptions += "-Yno-generic-signatures",       // Suppress generation of generic signatures in bytecode
  scalacOptions += "-Xfuture",                      // Enable "future language features"
  scalacOptions += "-opt:l:method,inline",          // Enable method optimizations, inlining
  scalacOptions += "-opt-warnings:none",            // Disable optimization warnings

  /** Project Structure **/
  resourceDirectory in Compile := baseDirectory(_/ "resources").value,
  scalaSource in Compile := baseDirectory(_/"src").value,
  scalaSource in Test := baseDirectory(_/"test").value,

  /** Testing **/
  scalacOptions in Test ++= Seq("-Yrangepos"),

  /** Macro Paradise **/
  resolvers += Resolver.sonatypeRepo("snapshots"),
  resolvers += Resolver.sonatypeRepo("releases"),
  addCompilerPlugin("org.scalamacros" % "paradise" % paradise_version cross CrossVersion.full),

  /** Release **/
  publishArtifact := false
)


/** Projects **/
lazy val utils  = project.settings(common)
lazy val emul   = project.settings(common)
lazy val models = project.settings(common)
lazy val forge  = project.settings(common).dependsOn(utils)
lazy val poly   = project.settings(common).dependsOn(utils)
lazy val argon  = project.settings(common).dependsOn(forge)
lazy val spatialTags = project.settings(common).dependsOn(utils, forge)

lazy val nova   = (project in file(".")).settings(common ++ Seq(
  fullClasspath in Runtime += baseDirectory(_/"target/scala-2.12/test-classes/").value,
  fullClasspath in Compile += baseDirectory(_/"target/scala-2.12/test-classes/").value
)).dependsOn(forge, emul, argon, models, poly, spatialTags)

lazy val apps = project.settings(common).dependsOn(nova)
