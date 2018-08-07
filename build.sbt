import sbt.Keys._

val smqdVersion = "0.4.1-SNAPSHOT"

val californiumVersion = "2.0.0-M10"

val `smqd-plugin-coap` = project.in(file(".")).settings(
  scalaVersion := "2.12.6",
  organization := "com.thing2x",
  name := "smqd-plugin-coap",
  version := "0.1.0-SNAPSHOT"
).settings(
  libraryDependencies ++= Seq(
    if (smqdVersion.endsWith("-SNAPSHOT"))
      "com.thing2x" %% "smqd-core" % smqdVersion changing() withSources() force()
    else
      "com.thing2x" %% "smqd-core" % smqdVersion
  ),
  libraryDependencies += "org.eclipse.californium" % "californium-core" % californiumVersion withSources(),
  resolvers += Resolver.sonatypeRepo("public")
)

