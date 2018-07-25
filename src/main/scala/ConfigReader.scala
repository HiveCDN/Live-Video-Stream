import com.typesafe.config.ConfigFactory

object ConfigReader {
  var fps: Int = _
  def initialize: Unit ={
    val config = ConfigFactory.load()
    fps=config.getInt("livevideostream.fps" )
  }
}
