package hivecdn.liveVideoStream

import java.io.File

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{HttpCookie, RawHeader}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.{Backoff, BackoffSupervisor}

import scala.concurrent.duration._
import akka.stream.scaladsl.{BroadcastHub, Keep, Source, SourceQueueWithComplete}
import akka.stream.{ActorMaterializer, OverflowStrategy, ThrottleMode, scaladsl}
import akka.util.ByteString

import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContextExecutor}

object WebServer extends App with CorsSupport {
  implicit val system: ActorSystem = ActorSystem("liveVideoStream")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  val MyMap: mutable.Map[String, String] = mutable.HashMap.empty[String,String]
  val (queue,source): (SourceQueueWithComplete[ByteString], Source[ByteString, NotUsed]) = Source
    .queue[ByteString](200  ,OverflowStrategy.dropHead)
    .preMaterialize()
  val imgSource: Source[ByteString, NotUsed] = source
    .throttle(ConfigReader.fps,1.second,ConfigReader.fps,ThrottleMode.shaping)
    .toMat(BroadcastHub.sink)(Keep.right).run().buffer(10, OverflowStrategy.dropHead)
  val boundaryMediaType = new MediaType.Multipart("x-mixed-replace", Map( "boundary" -> "--7b3cc56e5f51db803f790dad720ed50a" ) )
  val sourceSupervisorProps: Props = BackoffSupervisor.props(
    Backoff.onStop(
      SourceActor.props(queue),
      "SourceActor",
      2.seconds,
      5.seconds,
      randomFactor = 0.2
    ))

  def RIG: String = {
    java.util.UUID.randomUUID().toString
  }

  val encoderSupervisorProps: Props = BackoffSupervisor.props(
    Backoff.onStop(
      EncoderActor.props,
      "EncoderActor",
      2.seconds,
      5.seconds,
      randomFactor = 0.2
    ))
  val sourceRef: ActorRef = system.actorOf(sourceSupervisorProps,"SourceSupervisor")
  val baseFile: String = new File(ConfigReader.dir).getCanonicalPath
  val route: Route = path("") {
    getFromResource("index.html")
  } ~
  path("stream.mjpg") {
    get {
      respondWithHeader( RawHeader("Cache-Control","no-cache, private") ){
        complete(HttpEntity(ContentType(boundaryMediaType), imgSource ) )
      }
    }
  } ~
  path( "video" / Segment ){ name =>
    val file = new File(s"${ConfigReader.dir}/$name")
    if( !file.getCanonicalPath.startsWith(baseFile) ){
      complete(StatusCodes.BadGateway)
    }else {
      val returnRoute: Route = getFromFile(file)
      val cur = System.currentTimeMillis()
      MyMap.foreach { pair =>
        if (cur - pair._2.toLong > 5000)
          MyMap.remove(pair._1)
      }
      sourceRef ! OnlineUserUpdate(MyMap.size)
      optionalCookie("DASHUID") {
        case Some(id) =>
          MyMap += (id.value -> cur.toString)
          returnRoute
        case None =>
          setCookie(HttpCookie("DASHUID", RIG , Some(DateTime.apply(cur+1000*60*60)))) {
            returnRoute
          }
      }
    }
  } ~ path( "mjpeg" ){
    get {
      complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<!DOCTYPE html><html><body><img src=\"./stream.mjpg\"></body></html>"))
    }
  }

  Http().bindAndHandle(corsHandler(route),"0.0.0.0",ConfigReader.port)
  val encoderRef:ActorRef = system.actorOf(encoderSupervisorProps,"EncoderSupervisor")
  scaladsl.Source.tick(24.hours,24.hours,NotUsed).map(_ => encoderRef ! RestartMessage ).runWith(scaladsl.Sink.ignore)
  Await.result(system.whenTerminated, Duration.Inf)
}
