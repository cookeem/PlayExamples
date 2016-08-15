package demo

import java.io.FileReader

import org.apache.commons.csv.{CSVParser, CSVFormat}

import scala.collection.JavaConversions._

/**
  * Created by cookeem on 16/7/21.
  */
object CsvTest1 extends App {
  val in = new FileReader("/Volumes/Share/Download/HSS/SZHHSS07/188.2.138.22/proclog_20160721_103015_28943.csv")
  val records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(in)
  var i = 0
  records.foreach{ record =>
    i += 1
    if (i < 10) {
      record.toMap.foreach{ case (k,v) =>
        println(s"$k -> $v")
      }
      println("#############################################")
    }
  }
  records.close()
  in.close()

  CSVParser.parse("a,b,c", CSVFormat.RFC4180)
  CSVFormat.RFC4180.withFirstRecordAsHeader()
  CSVParser.parse("a,b,c,asdf&", CSVFormat.RFC4180.withFirstRecordAsHeader()).head.toArray
}
