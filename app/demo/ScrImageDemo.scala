package demo

import java.io.{FileInputStream, File}

import com.sksamuel.scrimage.{Pixel, X11Colorlist, Color, Image}
import com.sksamuel.scrimage.canvas._
import com.sksamuel.scrimage.nio.PngWriter

/**
  * Created by cookeem on 16/3/21.
  */
object ScrImageDemo extends App {
  implicit val writer = PngWriter.NoCompression
  val image = Image.fromFile(new File("/Volumes/Share/Download/scrimage-master/scrimage-core/src/test/resources/bird_486_324.png"))
  val font = Font.createTrueType(new FileInputStream("/Volumes/Share/Download/scrimage-master/scrimage-core/src/test/resources/fonts/Roboto-Black.ttf"))
  image.filter(new CaptionFilter(
    "This is an example of a big scary mudsucking fish",
    padding = Padding(40, 40, 20, 20),
    textSize = 22,
    textAlpha = 1,
    antiAlias = true, // anti alias implementation is different on openjdk vs oraclejdk, so tests can't use it reliably
    font = font
  )).output(new File("hello.png"))
  image.filter(new CaptionFilter(
    "This is an example of a big scary mudsucking fish",
    padding = Padding(40, 40, 20, 20),
    textSize = 22,
    textAlpha = 1,
    antiAlias = true, // anti alias implementation is different on openjdk vs oraclejdk, so tests can't use it reliably
    font = font
  )).removeTransparency(Color.White).output(new File("hello4.png"))
  image.flipX.output(new File("hello5.png"))

  val blank = Image.filled(300, 200, X11Colorlist.White)
  val canvas = new Canvas(blank).draw(
    Line(10, 5, 20, 25),
    Line(30, 50, 30, 200),
    Line(100, 100, 120, 120)
  )
  val img = canvas.image
  img.output(new File("hello2.png"))
  val black = Pixel(X11Colorlist.Black.toInt)

  val marked = image.filter(new WatermarkCoverFilter("watermark", size = 36, antiAlias = true, alpha = 0.3))
  marked.output(new File("hello3.png"))
}
