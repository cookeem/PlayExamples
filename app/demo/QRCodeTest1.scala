package demo

import java.awt.geom.RoundRectangle2D
import java.awt.{BasicStroke, Color}
import java.awt.image.BufferedImage
import java.io.File
import java.util
import javax.imageio.ImageIO

import com.google.zxing.{BarcodeFormat, MultiFormatWriter, EncodeHintType}
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
  * Created by cookeem on 16/7/20.
  */
object QRCodeTest1 extends App {
  val contents = "http://blog.csdn.net/sanfye/article/details/45749139"
  val width = 430
  val height = 430
  val format = "gif"
  val hints = new util.Hashtable[EncodeHintType, Any]()
  // 指定纠错等级,纠错级别（L 7%、M 15%、Q 25%、H 30%）
  hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
  // 内容所使用字符集编码
  hints.put(EncodeHintType.CHARACTER_SET, "utf-8")
  //      hints.put(EncodeHintType.MAX_SIZE, 350);//设置图片的最大值
  //      hints.put(EncodeHintType.MIN_SIZE, 100);//设置图片的最小值
  //设置二维码边的空度，非负数
  hints.put(EncodeHintType.MARGIN, 1)
  val bitMatrix = new MultiFormatWriter().encode(contents,
    BarcodeFormat.QR_CODE,
    width, //条形码的宽度
    height, //条形码的高度
    hints
  )
  val outputFile = new File(s"qrcode.$format")
  val image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
  for (x <- 0 until width) {
    for (y <- 0 until height) {
      val color = if (bitMatrix.get(x, y)) {
        Color.black.getRGB
      } else {
        Color.white.getRGB
      }
      image.setRGB(x, y, color)
    }
  }

  val g2 = image.createGraphics()
  val logo = ImageIO.read(new File("logo.png"))
  g2.drawImage(logo,width/5*2, height/5*2, width/5, height/5, null)
  val stroke = new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
  g2.setStroke(stroke)
  val round = new RoundRectangle2D.Float(width/5*2, height/5*2, width/5, height/5,20,20)
  g2.setColor(Color.white)
  g2.draw(round)
  val stroke2 = new BasicStroke(1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
  g2.setStroke(stroke2)
  val round2 = new RoundRectangle2D.Float(width/5*2+2, height/5*2+2, width/5-4, height/5-4,20,20)
  g2.setColor(new Color(128,128,128))
  g2.draw(round2)
  g2.dispose()
  image.flush()

  ImageIO.write(image, format, outputFile)
}
