name := "Live-Video-Stream"

version := "0.1"

scalaVersion := "2.12.6"

libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "2.5.11"
libraryDependencies += "com.typesafe.akka" %% "akka-http"   % "10.1.1"
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.5.14"
libraryDependencies += "net.bramp.ffmpeg" % "ffmpeg" % "0.6.2"
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"
libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.25"
libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % "2.5.13"