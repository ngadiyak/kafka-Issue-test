name := "kafkaExample"

version := "0.1"

scalaVersion := "2.13.5"

libraryDependencies ++= Seq(
  "org.apache.kafka" % "kafka-clients" % "2.7.0",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "org.testcontainers" % "kafka" % "1.15.1",
  "com.dimafeng" %% "testcontainers-scala" % "0.38.8",
  "org.scalatest" %% "scalatest" % "3.2.7"
)

Test / fork := true
Test / cancelable := true
