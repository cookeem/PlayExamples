package demo

import java.util.concurrent.Executors

import org.jsoup.Jsoup
import org.joda.time.DateTime
import org.openqa.selenium.Proxy
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.remote.{CapabilityType, DesiredCapabilities}

import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scala.util.Random

/**
  * Created by cookeem on 16/8/17.
  */
object HttpAutoProxy extends App {
  implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(50))
  val url = "http://www.youdaili.net/Daili/guonei/"
  val liElms = Jsoup.connect(url).timeout(10 * 1000).get().select("div.newslist_body > ul.newslist_line > li")
  val datetime = new DateTime()
  val currentTimeStr = datetime.toString("yyyy-MM-dd")
  val beforeTimeStr = datetime.plusDays(-7).toString("yyyy-MM-dd")
  val articles = liElms.map{ liElm =>
    val href = liElm.select("a").attr("href")
    val articleTime = liElm.select("span.articlelist_time").text()
    (href, articleTime)
  }.filter { case (href, articleTime) => articleTime <= currentTimeStr && articleTime >= beforeTimeStr}
  val ipListStr = articles.map { case (href, articleTime) =>
    val doc = Jsoup.connect(href).timeout(10 * 1000).get().select("div.newsdetail_cont > div.cont_font > p > span")
    doc.map { elm =>
      elm.select("br").append("##newline##")
      elm.text().replace("##newline##","\n")
    }.mkString.trim
  }.mkString("\n")

  var totalCount = 0
  var successCount = 0
  var failCount = 0
  var ipListActive = Array[(String, Int)]()
  var ipList = Array[(String, Int)]()

  ipListStr.split("\n").foreach{ str =>
    val arr = str.trim().split("@")(0).split(":")
    if (arr.length == 2) {
      totalCount += 1
      try {
        val ip = arr(0)
        val port = arr(1).toInt
        ipList = ipList :+ (ip, port)
        val doc = Future {
          Jsoup.connect("http://weixin.inheater.com/manage/").timeout(10 * 1000).proxy(ip, port).get()
        }

        doc.onComplete {
          case Success(_) =>
            successCount += 1
            ipListActive = ipListActive :+ (ip, port)
            println(s"[SUCCESS] $ip:$port")
            if (successCount + failCount == totalCount) {
              println(s"# total: $totalCount, success: $successCount, fail: $failCount")
            }
          case Failure(e) =>
            failCount += 1
            println(s"[FAIL] $ip:$port, ${e.getClass}")
            if (successCount + failCount == totalCount) {
              println(s"# total: $totalCount, success: $successCount, fail: $failCount")
            }
        }
      } catch {
        case e: Throwable =>
          println(s"error! ${e.getClass}, ${e.getMessage}, ${e.getCause}")
      }
    }
  }

  val random = new Random()
  val (ip, port) = ipListActive(random.nextInt(ipListActive.length))

  val proxy = new Proxy()
  proxy.setHttpProxy(s"$ip:$port")
  val cap = new DesiredCapabilities()
  cap.setCapability(CapabilityType.PROXY, proxy)
  val driver = new FirefoxDriver(cap)
  driver.get("http://weixin.inheater.com/manage/")
  val source = driver.getPageSource
  println(source)
  driver.close()
}
