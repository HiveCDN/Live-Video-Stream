package hivecdn.liveVideoStream

import com.typesafe.config.ConfigFactory

object ConfigReader {
  val config = ConfigFactory.load()
  var fps: Int = config.getInt("livevideostream.fps" )
  var level: Int = config.getInt("livevideostream.level")
}
