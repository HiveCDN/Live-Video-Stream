package hivecdn.liveVideoStream

import java.util.concurrent.TimeUnit

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, Props}
import akka.stream.{ActorMaterializer, scaladsl}

import scala.concurrent.duration._
import sys.process._

object EncoderActor {
  def props: Props = Props(new EncoderActor)
}

class EncoderActor extends Actor with ActorLogging{

  var ffmpegProcess: Process = _

  override def preStart(): Unit = {
    super.preStart()
    log.info("Encoder Actor started!")
    var command: String = ""
    if( ConfigReader.level != 1 ) {
      command = s"ffmpeg " +
        s"-threads 4 " +
        s"-y " +
        s"-re " +
        s"-i http://localhost:1234/stream.mjpg " +
        s"-an " +
        s"-sn " +
        s"-vf yadif=0 " +
        s"-c:v libx264 " +
        s"-x264opts keyint=${ConfigReader.fps}:min" +
        s"-keyint=${ConfigReader.fps}:no" +
        s"-scenecut " +
        s"-r ${ConfigReader.fps} " +
        s"-bf 1 " +
        s"-b_strategy 0 " +
        s"-sc_threshold 0 " +
        s"-pix_fmt yuv420p " +
        s"-map 0:v:0 " +
        s"-map 0:v:0 " +
        s"-map 0:v:0 " +
        s"-b:v:0 250k " +
        s"-g ${ConfigReader.fps * 2} " +
        s"-filter:v:0 scale=" +
        s"-2:240 " +
        s"-profile:v:0 baseline " +
        s"-b:v:1 750k " +
        s"-g ${ConfigReader.fps * 2} " +
        s"-filter:v:1 scale=" +
        s"-2:480 " +
        s"-profile:v:1 baseline " +
        s"-b:v:2 1500k " +
        s"-g ${ConfigReader.fps * 2} " +
        s"-filter:v:2 scale=" +
        s"-2:720 " +
        s"-profile:v:2 baseline " + //change this
        s"-f dash " +
        s"-use_timeline 1 " +
        s"-use_template 1 " +
        s"-adaptation_sets id=0,streams=v " +
        s"-min_seg_duration 2000000 " +
        s"-remove_at_exit 1 video/test.mpd"
    }else{
      command = s"ffmpeg -re -i http://localhost:1234/stream.mjpg -an -sn -vf yadif=0 -c:v libx264 " +
        s"-threads 4 " +
        s"-x264opts keyint=${ConfigReader.fps}:min" +
        s"-keyint=${ConfigReader.fps}:no" +
        s"-scenecut " +
        s"-r ${ConfigReader.fps} " +
        s"-g ${ConfigReader.fps*2} " +
        s"-f dash " +
        s"-use_timeline 1 " +
        s"-use_template 1 " +
        s"-min_seg_duration 2000000 " +
        s"-remove_at_exit 1 video/test.mpd"
    }
    log.info(command)
//    Process(command).lineStream
    ffmpegProcess = Process(command).run( ProcessLogger( _ => () ) )
    log.info("ENCODER STARTED!!!!")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    var deleteChunkNum:Int = 1
    var deleteCommand: String = ""
    val chunkDeleteEveryXMinute: Long = 1
    val chunkDeleteRange:Int = 60*chunkDeleteEveryXMinute.toInt/2 - 1
    log.info("CREATING GARBAGE COLLECTOR")
    scaladsl.Source.tick(FiniteDuration.apply(chunkDeleteEveryXMinute*2,TimeUnit.MINUTES),FiniteDuration.apply(chunkDeleteEveryXMinute,TimeUnit.MINUTES),NotUsed).map { _ =>
      log.info("DELETE TICK")
      for( representationId <- 0 until ConfigReader.level )
        for (chunkIterator <- deleteChunkNum to deleteChunkNum + chunkDeleteRange) {
          deleteCommand = "rm video/chunk-stream"+representationId+"-"+"%05d".format(chunkIterator)+".m4s"
          log.info(deleteCommand)
          Process(deleteCommand).run( ProcessLogger( _ => () ) )
        }
      deleteChunkNum+=chunkDeleteRange+1
    }.runWith(scaladsl.Sink.ignore)
  }

  override def receive: Receive = {
    case RestartMessage =>
      ffmpegProcess.destroy()
      throw new Exception
  }
}

