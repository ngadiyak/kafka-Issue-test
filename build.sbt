name := "kafkaExample"

version := "0.1"

scalaVersion := "2.13.5"

libraryDependencies += "org.scalactic" %% "scalactic" % "3.2.7"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.7" % Test
libraryDependencies += "com.dimafeng" %% "testcontainers-scala" % "0.38.8" % "test"
libraryDependencies += "org.testcontainers" % "kafka" % "1.15.1"
libraryDependencies += "org.apache.kafka" % "kafka-clients" % "2.7.0"
libraryDependencies += "io.netty" % "netty-all" % "4.0.4.Final"
libraryDependencies += "io.monix" %% "monix" % "3.3.0"

Test / fork := true
