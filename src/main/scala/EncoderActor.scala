import akka.actor.{Actor, ActorLogging, Props}
import net.bramp.ffmpeg._
import net.bramp.ffmpeg.builder.FFmpegBuilder

object EncoderActor {
  def props(fps: Int) : Props = Props(new EncoderActor(fps) )
}

class EncoderActor(fps: Int) extends Actor with ActorLogging{

  override def preStart(): Unit = {
    super.preStart()
    log.info("Encoder Actor started!")
    val ffmpeg: FFmpeg = new FFmpeg("/usr/local/bin/ffmpeg")
    val ffprobe: FFprobe = new FFprobe( "/usr/local/bin/ffprobe")
    val builder: FFmpegBuilder = new FFmpegBuilder()
      .readAtNativeFrameRate()
      .setInput("http://localhost:1234/stream")
      .addOutput("video/test.mpd")
      .setVideoCodec("libx264")
      .setVideoFrameRate(fps,1)
      .addExtraArgs("-f","dash",
        "-window_size","3",
        "-extra_window_size","9",
        "-min_seg_duration","1000000",
        "-use_template","1",
        "-use_timeline","1")
      .done()

    val executor = new FFmpegExecutor(ffmpeg,ffprobe)
    executor.createJob(builder).run()

  }

  override def receive: Receive = {
    case _ => //ignore
  }
}

