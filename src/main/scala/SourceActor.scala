import akka.NotUsed
import akka.actor.{Actor, ActorLogging, Props}
import akka.stream.scaladsl.SourceQueueWithComplete
import akka.util.ByteString
import akka.stream.{ActorMaterializer, scaladsl}

import java.awt.{Color, Graphics}
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

object SourceActor{
  def props(givenQueue :SourceQueueWithComplete[ByteString] , interval: Int ): Props = Props(new SourceActor(givenQueue,interval))
}

class SourceActor( sourceQueue: SourceQueueWithComplete[ByteString] , INTERVAL: Int) extends Actor with ActorLogging{

  var globalStart: Long = 0
  var prevImage: ByteString = _
  var startTime: Long = 0
  var timePassed: Long = 0
  var waitTime: Long = 0
  var offeredImages: Long = 0

  override def preStart(): Unit = {
    super.preStart()
    log.info("Source actor started!")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    prevImage = getImage
    scaladsl.Source.repeat[NotUsed](NotUsed)
      .map[Unit]( _ =>{
        if( globalStart == 0 ) globalStart = System.currentTimeMillis()
        timePassed = System.currentTimeMillis()-globalStart
        while( (System.currentTimeMillis()-globalStart)/INTERVAL > offeredImages ){
            sourceQueue offer prevImage
            offeredImages+=1
          }
        prevImage=getImage
        sourceQueue offer prevImage
        offeredImages+=1
        waitTime = INTERVAL - (System.currentTimeMillis()-globalStart)%INTERVAL
        startTime = System.currentTimeMillis()
        while( System.currentTimeMillis() - startTime < waitTime ){
          System.currentTimeMillis()
        }
      })
      .runWith(scaladsl.Sink.ignore)
  }

  override def postStop(): Unit = {
    log.warning("Source Actor Stopped!!")
    super.postStop()
  }

  override def receive: Receive = {
    case _ => //ignore
  }

  def getImage: ByteString = {
    val t: Long = System.currentTimeMillis()-globalStart
    val img = new BufferedImage(100, 50 , BufferedImage.TYPE_INT_RGB)
    val g: Graphics = img.getGraphics
    g.setColor(Color.getHSBColor(
      (t % 10000f) / 10000f,
      0.5f,
      0.25f))
    g.fillRect(0, 0, 100, 50)
    g.setColor(Color.WHITE)
    g.drawString("" + t / 10, 10 , 15)
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
