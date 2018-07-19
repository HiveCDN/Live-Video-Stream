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

import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

object WebServer extends App {
  implicit val system: ActorSystem = ActorSystem("clusterSystem")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  val MyMap: mutable.Map[String, String] = mutable.HashMap.empty[String,String]
  final val INTERVAL: Int = 35
  val (queue,source): (SourceQueueWithComplete[ByteString], Source[ByteString, NotUsed]) = Source
    .queue[ByteString](60 ,OverflowStrategy.dropHead)
    .preMaterialize()
  val imgSource: Source[ByteString, NotUsed] = source.throttle(25,1.second,25,ThrottleMode.shaping).toMat(BroadcastHub.sink)(Keep.right).run()
  val boundaryMediaType = new MediaType.Multipart("x-mixed-replace", Map( "boundary" -> "boundary" ) )
  val sourceSupervisorProps: Props = BackoffSupervisor.props(
    Backoff.onStop(
      SourceActor.props(queue,INTERVAL),
      "SourceActor",
      2.seconds,
      5.seconds,
      randomFactor = 0.2
    ))

  def RIG: String = {
    val rand= scala.util.Random.alphanumeric.take(8).mkString("")
    println(rand)
    rand
  }

  val encoderSupervisorProps: Props = BackoffSupervisor.props(
    Backoff.onStop(
      EncoderActor.props(1000/INTERVAL),
      "EncoderActor",
      2.seconds,
      5.seconds,
      randomFactor = 0.2
    ))
  val SourceRef: ActorRef = system.actorOf(sourceSupervisorProps,"SourceSupervisor")
  system.actorOf(encoderSupervisorProps,"EncoderSupervisor")
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
  }

  Http().bindAndHandle(route,"0.0.0.0",1234)

  Await.result(system.whenTerminated, Duration.Inf)
}
