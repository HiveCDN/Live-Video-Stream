import com.typesafe.sbt.packager.docker._

name := "testvideoserver"
scalaVersion := "2.12.6"

libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "2.5.11"
libraryDependencies += "com.typesafe.akka" %% "akka-http"   % "10.1.1"
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.5.14"
libraryDependencies += "net.bramp.ffmpeg" % "ffmpeg" % "0.6.2"
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"
libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.25"
libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % "2.5.13"

enablePlugins(JavaServerAppPackaging)

dockerEntrypoint ++= Seq(
  """-Dakka.remote.netty.tcp.hostname="$(eval "echo $AKKA_REMOTING_BIND_HOST")"""",
  """-Dakka.management.http.hostname="$(eval "echo $AKKA_REMOTING_BIND_HOST")""""
)
dockerCommands :=
  dockerCommands.value.flatMap {
    case ExecCmd("ENTRYPOINT", args @ _*) => Seq(Cmd("ENTRYPOINT", args.mkString(" ")))
    case v => Seq(v)
  }
version := "1.0"
dockerUsername := Some("hivecdn")
dockerCommands += Cmd("USER", "root")
dockerCommands += ExecCmd("RUN","apt-get","upgrade")
dockerCommands += ExecCmd("RUN","mkdir","/usr/local/bin/ffmpeg")
dockerCommands += ExecCmd("RUN","mkdir","video")
dockerCommands += ExecCmd("RUN","wget","http://johnvansickle.com/ffmpeg/releases/ffmpeg-release-64bit-static.tar.xz")
dockerCommands += ExecCmd("RUN","tar","xf","ffmpeg-release-64bit-static.tar.xz")
//dockerCommands += ExecCmd("RUN","ls","ffmpeg-4.0.1-64bit-static/")
dockerCommands += ExecCmd("RUN","mv","*64bit-static/ffmpeg","/usr/local/bin/ffmpeg/")
dockerCommands += ExecCmd("RUN","ln","-s","/usr/local/bin/ffmpeg/ffmpeg","/usr/bin/ffmpeg")
dockerCommands += ExecCmd("RUN","ffmpeg","-version")