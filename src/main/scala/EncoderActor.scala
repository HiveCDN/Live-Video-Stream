import akka.actor.{Actor, ActorLogging, Props}
import sys.process._

object EncoderActor {
  def props(fps: Int) : Props = Props(new EncoderActor(fps) )
}

class EncoderActor(fps: Int) extends Actor with ActorLogging{

  override def preStart(): Unit = {
    super.preStart()
    log.info("Encoder Actor started!")
    val command: String = "ffmpeg -re -i http://localhost:1234/stream -vf yadif=0 -r 25 -vcodec libx264 " +
      //-g 50 => group images by 50 => make a chunk with every 50 frames => since output framerate is 25 makes a chunk every 2 seconds
      "-g 50 " +
      "-f dash -window_size 3 -extra_window_size 9 -use_template 1 -use_timeline 0 " +
      //seg duration must be group images by/frame rate which is 50/25 right now
      "-min_seg_duration 2000000 " +
      "-remove_at_exit 1 video/test.mpd"
//    Process(command).lineStream
    val x: Process = Process(command).run( ProcessLogger(_ => () ) )
    if( x.exitValue() == 1 ) throw new Exception

  }

  override def receive: Receive = {
    case _ => //ignore
  }
}

