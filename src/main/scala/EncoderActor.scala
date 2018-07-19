import akka.actor.{Actor, ActorLogging, Props}
import sys.process._

object EncoderActor {
  def props(fps: Int) : Props = Props(new EncoderActor(fps) )
}

class EncoderActor(fps: Int) extends Actor with ActorLogging{

  override def preStart(): Unit = {
    super.preStart()
    log.info("Encoder Actor started!")
    val command: String = "encoder/ffmpeg -r 25 -i http://localhost:1234/stream -vf yadif=0 -r 25 -vcodec libx264 " +
      "-g 50 " +
      "-f dash -window_size 3 -extra_window_size 9 -use_template 1 -use_timeline 0 " +
      "-seg_duration 2 -remove_at_exit 1 video/test.mpd"
//    Process(command).lineStream
    val x: Process = Process(command).run( ProcessLogger(_ => () ) )
    if( x.exitValue() == 1 ) throw new Exception

  }

  override def receive: Receive = {
    case _ => //ignore
  }
}

