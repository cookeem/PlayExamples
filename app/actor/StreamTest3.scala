package actor

import java.io.StringReader
import java.nio.file.Paths

import akka.actor._
import akka.stream._
import akka.stream.scaladsl._
import akka.util.ByteString
import org.apache.commons.csv.{CSVRecord, CSVParser, CSVFormat}

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.concurrent.Future

/**
 * Created by cookeem on 16/3/11.
 */
object StreamTest3 extends App {
  //materializer都是在actorSystem中运行
  implicit val system = ActorSystem("QuickStart")
  //stream都是在materializer中运行,必须进行implicit
  implicit val materializer = ActorMaterializer()
  //必须import一个executionContext
  import materializer.executionContext

  //简单版
  val source = Source[Int](1 to 10)
  //必须指定foreach的Int类型,对接source
  val sink = Sink.foreach[Int](i => println(s"receive $i"))
  val graph = source.to(sink)
  graph.run()

  //与actor集成
  def run(actor: ActorRef): Future[Unit] = {
    Future { Thread.sleep(300); actor ! 1 }
    Future { Thread.sleep(200); actor ! 2 }
    Future { Thread.sleep(100); actor ! 3 }
  }
  //flow简单样例
  val flow1 = Flow[Int].map(elem => elem * -1)
  val flow2 = Flow[Int].map(elem => elem * 2)
  source.via(flow1).via(flow2).to(sink).run()
  val flow3 = Flow.fromSinkAndSource(sink,source)
  flow3.runWith(source,sink)

  val src1 = Source(List(1,1,3,4,5,3))
  val src2 = Source(6 to 10)
  val zipSrc = src1.zip(src2)
  val groupFlow = zipSrc.groupBy(6, _._1)
  groupFlow.to(Sink.foreach(println)).run()

  FileIO.fromPath(Paths.get("build.sbt")).runForeach{bs =>
    val s = bs.utf8String
    println(s"@@@ $s")
  }

  var headers = Array[String]()
  var i = 0
  var headerLine = ""
  FileIO.fromPath(Paths.get("/Volumes/Share/Download/HSS/SZHHSS07/188.2.138.22/proclog_20160721_103015_28943.csv")).via(
    Framing.delimiter(
      ByteString("\"\n"),
      maximumFrameLength = 1024 * 1024,
      allowTruncation = true
    )
  ).map{bs =>
    var record = Map[String, String]()
    var recordLine = bs.utf8String + CSVFormat.RFC4180.getQuoteCharacter
    i = i + 1
    if (i == 1) {
      if (recordLine.split(CSVFormat.RFC4180.getRecordSeparator).length > 1) {
        headerLine = recordLine.split(CSVFormat.RFC4180.getRecordSeparator)(0).trim
        val csvHeader = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(new StringReader(headerLine)).getHeaderMap
        headers = csvHeader.toArray.sortBy{ case (k,v) => v}.map{ case (k,v) => k}
        recordLine = recordLine.split(CSVFormat.RFC4180.getRecordSeparator).drop(1).mkString(CSVFormat.RFC4180.getRecordSeparator)
      }
    }
    if (headers.length > 0) {
      CSVParser.parse(recordLine, CSVFormat.RFC4180.withHeader(headers: _*)).head.toMap.mkString(CSVFormat.RFC4180.getRecordSeparator)
    } else {
      null
    }
  }.dropWhile(_ == null)
//    .take(4)
    .runForeach{s =>
      println(s)
      println("#############################")
    }
  println(s"line count: $i")
}
