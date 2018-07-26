package hivecdn.liveVideoStream
import java.io.File

import com.typesafe.config.ConfigFactory

object ConfigReader {
  private val config = ConfigFactory.load()
  private val cpuCount:Int = Runtime.getRuntime.availableProcessors()
  val fps: Int = config.getInt("livevideostream.fps" )
  val level: Int = config.getInt("livevideostream.level")
  val port: Int = config.getInt("livevideostream.port")
  var dir: String ={
    if( config.hasPath("livevideostream.videoDirectory") ) {
      val userDir: String =config.getString("livevideostream.videoDirectory")
      if( userDir.isEmpty ){
        "./"
      }else{
        if( userDir.endsWith("/") ){
          userDir
        }else{
          userDir+"/"
        }
      }
    }else{
      "./"
    }
  }
  var cores: Int ={
    if(config.hasPath("livevideostream.numberOfCores")){
      math.min(cpuCount,config.getInt("livevideostream.numberOfCores") )
    }else{
      cpuCount
    }
  }
  require(cores>0)
//  println(dir)
  require( new File(dir).exists() , "No Such Directory!!!" )
  dir = dir+"livevideostream"
//  println(dir)
  println("Number of cores will be used:"+cores)
}
