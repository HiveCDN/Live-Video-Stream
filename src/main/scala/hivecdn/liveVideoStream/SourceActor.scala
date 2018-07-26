package hivecdn.liveVideoStream
import akka.NotUsed
import akka.actor.{Actor, ActorLogging, Props}
import akka.stream.scaladsl.SourceQueueWithComplete
import akka.util.ByteString
import akka.stream.{ActorMaterializer, OverflowStrategy, scaladsl}
import java.awt._

import scala.concurrent.duration._
import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import java.io._
import java.net.{HttpURLConnection, URL}
import java.util.concurrent.TimeUnit

import akka.http.scaladsl.model.DateTime

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.atomic.AtomicLong

object SourceActor{
  def props(givenQueue :SourceQueueWithComplete[ByteString]): Props = Props(new SourceActor(givenQueue))
}

class SourceActor( sourceQueue: SourceQueueWithComplete[ByteString]) extends Actor with ActorLogging{

  var frame: AtomicLong = new AtomicLong(0)
  var onlineUsers: Long = 0
  var logo: BufferedImage = _
  var startTime: Long = 0
  var baseImage: BufferedImage = _
  var FPS: Int = _
  val WIDTH: Int = 1280
  val HEIGHT: Int = 720
  val font: Font = new Font(Font.DIALOG,Font.BOLD,HEIGHT/20)
  val stroke: Stroke = new BasicStroke(10 )

  override def preStart(): Unit = {
    super.preStart()
    log.info("Source actor started!")
    getLogo
    prepareBaseImage
    FPS=ConfigReader.fps
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val executionContext: ExecutionContextExecutor = context.dispatcher
    startTime = System.currentTimeMillis()
    scaladsl.Source.repeat[NotUsed](NotUsed)
      .map{ _ =>
        frame.incrementAndGet() }
      .mapAsync( ConfigReader.cores ){ currentFrame =>
         Future( getImage(currentFrame) -> currentFrame ) }
      .buffer( FPS , OverflowStrategy.backpressure )
      .map{ image =>
        sourceQueue offer image._1
        var diff: Long = Math.max(0,(System.currentTimeMillis()-500-startTime)/(1000/FPS)-frame.get())
        frame.addAndGet(diff)
        while( diff > 0 ){
          log.info("RECOVERY")
          sourceQueue offer image._1
          diff -= 1
        }
        Math.max(startTime+frame.get()*1000/FPS-System.currentTimeMillis()-2000,0)
      }
      .flatMapConcat { timeToWait =>
        scaladsl.Source.single(NotUsed)
          .delay(FiniteDuration.apply(timeToWait, TimeUnit.MILLISECONDS))
      }
      .runWith(scaladsl.Sink.ignore)
    var prevFrame:Long = 0
    scaladsl.Source.tick(1.second,1.second,NotUsed).map { _ =>
      log.info("FPS: " + (frame.get() - prevFrame))
      prevFrame = frame.get()
    }.runWith(scaladsl.Sink.ignore)
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

  def prepareBaseImage: Unit = {
    baseImage = new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_RGB)
    val g: Graphics = baseImage.getGraphics
    g.setColor(Color.WHITE)
    g.fillRect(0, 0, WIDTH, HEIGHT)
    g.drawImage( logo , WIDTH-325 , 0 , null)
    g.setColor(Color.BLACK)
    g.fillOval(WIDTH/2-HEIGHT/4, HEIGHT/4 , HEIGHT/2 , HEIGHT/2 )
    g.setColor(Color.WHITE)
    g.fillOval(WIDTH/2-HEIGHT/4+10, HEIGHT/4+10 , HEIGHT/2-20 , HEIGHT/2-20 )
    g.dispose()
  }

  def getLogo: Boolean = {
    try{
      val url: URL = new URL("https://hivecdn.com/assets/images/hivecdn-logo.png")
      val connection: HttpURLConnection = url.openConnection().asInstanceOf[HttpURLConnection]
      connection.setRequestMethod("GET")
      val in: InputStream = connection.getInputStream
      val out = new BufferedOutputStream(new FileOutputStream("logo.png"))
      val byteArray: Array[Byte] = Stream.continually(in.read).takeWhile(-1 !=).map(_.toByte).toArray
      out.write(byteArray)
      in.close()
      out.close()
      logo = ImageIO.read(new File("logo.png"))
      true
    }catch {
      case _:Exception => false
    }
  }


  def getImage(currentFrame: Long): ByteString = {
//    log.info("GETIMAGE")
    val img = new BufferedImage(baseImage.getColorModel,baseImage.copyData(null),baseImage.getColorModel.isAlphaPremultiplied,null)
    val g: Graphics = img.getGraphics
    g.setColor(Color.BLACK)
    g.setFont(font)
    g.drawString( DateTime.apply(startTime+currentFrame*1000/FPS).toRfc1123DateTimeString() , WIDTH/100 , HEIGHT/20)
    g.drawString("ONLINE USERS:"+onlineUsers , WIDTH/100 , 3*HEIGHT/20)
    g.drawString("FRAME:"+currentFrame , WIDTH/100 , HEIGHT-HEIGHT/20 )
    val g2 = g.asInstanceOf[Graphics2D]
    g2.setStroke(stroke)
    g2.drawLine(WIDTH/2,HEIGHT/2,WIDTH/2+((HEIGHT/4-10) * Math.sin((360.0-(currentFrame%(FPS*5))*360.0/(FPS*5))*Math.PI/180)).asInstanceOf[Int] , HEIGHT/2+( (HEIGHT/4-10) * Math.cos((360.0-(currentFrame%(FPS*5))*360.0/(FPS*5))*Math.PI/180)).asInstanceOf[Int] )
    g.dispose()
    g2.dispose()
    toJpeg(img)
  }

  private val NL = "\r\n"
  private val BOUNDARY = "--7b3cc56e5f51db803f790dad720ed50a"
  private val HEAD = NL + NL + BOUNDARY + NL + "Content-Type: image/jpeg" + NL + "Content-Length: "

  private def toJpeg(image: BufferedImage) : ByteString = {
    val stream: ByteArrayOutputStream = new ByteArrayOutputStream
    ImageIO.write( image,"jpg",stream)
    val BA: Array[Byte] = stream.toByteArray
    stream.close()
    val headers: Array[Byte] = (HEAD + BA.length + NL + NL).getBytes
    ByteString.fromArray(headers ++ BA)
  }
}
