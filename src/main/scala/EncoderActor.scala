import akka.actor.{Actor, ActorLogging, Props}
import sys.process._

object EncoderActor {
  def props: Props = Props(new EncoderActor)
}

class EncoderActor extends Actor with ActorLogging{

  override def preStart(): Unit = {
    super.preStart()
    log.info("Encoder Actor started!")
    val command: String = s"""ffmpeg -re -i http://localhost:1234/stream.mjpg -an -sn -vf yadif=0 -r ${ConfigReader.fps} -vcodec libx264 """ +
      //-g X => group images by X => make a chunk with every fps*2
      "-threads 4 " +
      s"""-g ${ConfigReader.fps*2} """ +
      "-f dash -use_template 1 -use_timeline 1 " +
      //seg duration must be group images by/frame rate seconds => convert to microseconds
      "-min_seg_duration 2000000 " +
      "-remove_at_exit 1 video/test.mpd"
    Process(command).lineStream
//    val x: Process = Process(command).run( ProcessLogger(_ => () ) )
//    if( x.exitValue() == 1 ) throw new Exception

  }

  override def receive: Receive = {
    case _ => //ignore
  }
}

