import akka.{Done, NotUsed}
import akka.actor.{ActorRef, ActorSystem, Cancellable, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{HttpCookie, HttpOrigin, Origin, RawHeader}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.{Backoff, BackoffSupervisor}

import scala.concurrent.duration._
import akka.stream.scaladsl.{BroadcastHub, Keep, Source, SourceQueueWithComplete}
import akka.stream.{ActorMaterializer, OverflowStrategy, ThrottleMode, scaladsl}
import akka.util.ByteString
import com.typesafe.config.ConfigFactory

import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

object WebServer extends App with CorsSupport {
  implicit val system: ActorSystem = ActorSystem("clusterSystem")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  val MyMap: mutable.Map[String, String] = mutable.HashMap.empty[String,String]
  val (queue,source): (SourceQueueWithComplete[ByteString], Source[ByteString, NotUsed]) = Source
    .queue[ByteString](200  ,OverflowStrategy.dropHead)
    .preMaterialize()
  ConfigReader.initialize
  val imgSource: Source[ByteString, NotUsed] = source.throttle(ConfigReader.fps,1.second,ConfigReader.fps,ThrottleMode.shaping).toMat(BroadcastHub.sink)(Keep.right).run()
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
  val SourceRef: ActorRef = system.actorOf(sourceSupervisorProps,"SourceSupervisor")
  system.actorOf(encoderSupervisorProps,"EncoderSupervisor")
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
  path("video" / Segment ){ name =>
    val returnRoute: Route = getFromFile(s"video/$name")
    optionalCookie("DASHUID"){
      case Some(id) =>
        val cur = System.currentTimeMillis()
        MyMap += ( id.value -> cur.toString )
        MyMap.foreach{ pair =>
          if(cur-pair._2.toLong > 5000)
            MyMap.remove(pair._1)
        }
        SourceRef ! OnlineUserUpdate(MyMap.size)
        returnRoute
      case None =>
        setCookie(HttpCookie("DASHUID",RIG)){
          returnRoute
        }
    }
  } ~ path( "mjpeg" ){
    get {
      complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<!DOCTYPE html><html><body><img src=\"./stream.mjpg\"></body></html>"))
    }
  }

  Http().bindAndHandle(corsHandler(route),"0.0.0.0",1234)

  Await.result(system.whenTerminated, Duration.Inf)
}
