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
).settings(
  // License
  organizationName := "UANGEL",
  startYear := Some(2018),
  licenses += ("EPL-2.0", new URL("http://www.eclipse.org/legal/epl-v20.html")),
  headerMappings := headerMappings.value + (HeaderFileType.scala -> HeaderCommentStyle.cppStyleLineComment),
  headerMappings := headerMappings.value + (HeaderFileType.conf -> HeaderCommentStyle.hashLineComment),
  headerLicense := Some(HeaderLicense.Custom(
    """
      |Copyright (c) 2018 UANGEL
      |
      |All rights reserved. This program and the accompanying materials
      |are made available under the terms of the Eclipse Public License v2.0
      |and Eclipse Distribution License v1.0 which accompany this distribution.
      |
      |The Eclipse Public License is available at
      |   http://www.eclipse.org/legal/epl-v20.html
      |and the Eclipse Distribution License is available at
      |   http://www.eclipse.org/org/documents/edl-v10.html.
    """.stripMargin))
).enablePlugins(AutomateHeaderPlugin)

