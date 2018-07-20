import akka.NotUsed
import akka.actor.{Actor, ActorLogging, Props}
import akka.stream.scaladsl.SourceQueueWithComplete
import akka.util.ByteString
import akka.stream.{ActorMaterializer, scaladsl}
import java.awt.{Color, Font, Graphics}

import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.ImageWriter
import javax.imageio.plugins.jpeg.JPEGImageWriteParam
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util
import java.util.Locale
import java.util.concurrent.TimeUnit

import akka.http.scaladsl.model.DateTime

import scala.concurrent.duration.FiniteDuration

object SourceActor{
  def props(givenQueue :SourceQueueWithComplete[ByteString] , interval: Int ): Props = Props(new SourceActor(givenQueue,interval))
}

class SourceActor( sourceQueue: SourceQueueWithComplete[ByteString] , INTERVAL: Int) extends Actor with ActorLogging{

  var globalStart: Long = 0
  var prevImage: ByteString = _
  var startTime: Long = 0
  var timePassed: Long = 0
  var offeredImages: Long = 0
  var onlineUsers: Long = 0

  override def preStart(): Unit = {
    super.preStart()
    log.info("Source actor started!")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    prevImage = getImage
    scaladsl.Source.repeat[NotUsed](NotUsed)
      .map[Long]( _ =>{
        if( globalStart == 0 ) globalStart = System.currentTimeMillis()
        timePassed = System.currentTimeMillis()-globalStart
//        log.info("Expected: "+timePassed/INTERVAL)
//        log.info("Actual: "+offeredImages)
        while( (System.currentTimeMillis()-globalStart)/INTERVAL > offeredImages ){
//          log.info("RECOVERY")
            sourceQueue offer prevImage
            offeredImages+=1
          }
        prevImage=getImage
        sourceQueue offer prevImage
        offeredImages+=1
        INTERVAL - (System.currentTimeMillis()-globalStart)%INTERVAL
      })
      .flatMapConcat(
        waitTime =>
          scaladsl.Source.single[NotUsed](NotUsed).delay(FiniteDuration.apply(waitTime,TimeUnit.MILLISECONDS))
      )
      .runWith(scaladsl.Sink.ignore)
  }

  override def postStop(): Unit = {
    log.warning("Source Actor Stopped!!")
    super.postStop()
  }

  override def receive: Receive = {
    case update: OnlineUserUpdate =>{
//      log.info("online user upadate: "+update.value)
      onlineUsers=update.value
    }
    case _ => //ignore
  }

  def getImage: ByteString = {
    val t: Long = System.currentTimeMillis()-globalStart
    val WIDTH: Int = 1280
    val HEIGHT: Int = 720
    val img = new BufferedImage(WIDTH, HEIGHT , BufferedImage.TYPE_INT_RGB)
    val g: Graphics = img.getGraphics
    g.setColor(Color.WHITE)
    g.fillRect(0, 0, WIDTH, HEIGHT)
    g.setColor(Color.BLACK)
    g.setFont(new Font(Font.DIALOG,Font.BOLD,HEIGHT/20))
    g.drawString( DateTime.now.toRfc1123DateTimeString() , WIDTH/100 , HEIGHT/20)
    g.drawString("ONLINE USERS:"+onlineUsers , WIDTH/100 , 2*HEIGHT/20)
    g.drawString("FRAME:"+offeredImages , WIDTH-12*HEIGHT/20 , HEIGHT-2*HEIGHT/20 )
    g.dispose()
    toJpeg(img, 100)
  }

  private val NL = "\r\n"
  private val BOUNDARY = "--boundary"
  private val HEAD = NL + NL + BOUNDARY + NL + "Content-Type: image/jpeg" + NL + "Content-Length: "

  private def toJpeg(image: BufferedImage, qualityPercent: Int) : ByteString = {
    if ((qualityPercent < 0) || (qualityPercent > 100)) throw new IllegalArgumentException("Quality out of bounds!")
    val quality = qualityPercent / 100f
    var writer: ImageWriter = null
    val iter: util.Iterator[ImageWriter] = ImageIO.getImageWritersByFormatName("jpg")
    if (iter.hasNext) writer = iter.next
    val stream = new ByteArrayOutputStream
    try {
      val ios = ImageIO.createImageOutputStream(stream)
      writer.setOutput(ios)
      val iwparam = new JPEGImageWriteParam(Locale.getDefault)
      iwparam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT)
      iwparam.setCompressionQuality(quality)
      writer.write(null, new IIOImage(image, null, null), iwparam)
      ios.flush()
      writer.dispose()
      ios.close()
      val BA: Array[Byte] = stream.toByteArray
      val headers: Array[Byte] = (HEAD+BA.length+NL+NL).getBytes
      val out=ByteString.fromArray(headers++BA)
      stream.close()
      out
    } catch {
      case e: IOException =>
        e.printStackTrace()
        null
    }
  }
}
