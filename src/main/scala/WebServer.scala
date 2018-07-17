import akka.{Done, NotUsed}
import akka.actor.{ActorSystem, Cancellable, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{HttpOrigin, Origin, RawHeader}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.{Backoff, BackoffSupervisor}

import scala.concurrent.duration._
import akka.stream.scaladsl.{Keep, Source, SourceQueueWithComplete}
import akka.stream.{ActorMaterializer, OverflowStrategy, ThrottleMode, scaladsl}
import akka.util.ByteString

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

object WebServer extends App {
  implicit val system: ActorSystem = ActorSystem("clusterSystem")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  val (queue,imgSource): (SourceQueueWithComplete[ByteString], Source[ByteString, NotUsed]) = Source
    .queue[ByteString](0,OverflowStrategy.backpressure)
    .preMaterialize()
  val boundaryMediaType = new MediaType.Multipart("x-mixed-replace", Map( "boundary" -> "boundary" ) )
  val sourceSupervisor: Props = BackoffSupervisor.props(
    Backoff.onStop(
      SourceActor.props(queue,40),
      "SourceActor",
      2.seconds,
      5.seconds,
      randomFactor = 0.2
    ))
  val encoderSupervisor: Props = BackoffSupervisor.props(
    Backoff.onStop(
      EncoderActor.props(25),
      "EncoderActor",
      2.seconds,
      5.seconds,
      randomFactor = 0.2
    ))
  system.actorOf(sourceSupervisor,"SourceSupervisor")
  system.actorOf(encoderSupervisor,"EncoderSupervisor")
  val route: Route = path("") {
    getFromFile("template/index.html")
  } ~
  path("stream") {
    get {
      respondWithHeader( RawHeader("Cache-Control","no-cache, private") ){
        complete(HttpEntity(ContentType(boundaryMediaType), imgSource ) )
      }
    }
  } ~
  path("video" / Segment ){ name =>
    getFromFile(s"video/$name")
  }
  Http().bindAndHandle( route , "localhost" , 1234 )

  Await.result(system.whenTerminated, Duration.Inf)
}
