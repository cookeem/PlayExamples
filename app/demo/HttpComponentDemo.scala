package demo

import java.io._

import org.apache.commons.io.IOUtils
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClients
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.openqa.selenium.{Keys, By, JavascriptExecutor, Dimension}
import org.openqa.selenium.firefox.{FirefoxProfile, FirefoxDriver}

import play.api.libs.json._

import scala.collection.JavaConversions._
import scala.util.Random


/**
  * Created by cookeem on 16/3/30.
  */
object HttpComponentDemo extends App {
  def inputStreamCopy(is: InputStream): (InputStream, InputStream) = {
    val baos = new ByteArrayOutputStream()
    val buffer = new Array[Byte](1024)
    var len = is.read(buffer)
    while (len > -1 ) {
      baos.write(buffer, 0, len)
      len = is.read(buffer)
    }
    baos.flush()
    val is1: InputStream = new ByteArrayInputStream(baos.toByteArray())
    val is2: InputStream = new ByteArrayInputStream(baos.toByteArray())
    (is1, is2)
  }

  def getUrlContent(url: String): String = {
    val httpClient = HttpClients.createDefault()
    val requestConfig = RequestConfig.custom().setConnectTimeout(5000).setConnectionRequestTimeout(5000).setSocketTimeout(5000).build()
    val httpGet: HttpGet = new HttpGet(url)
    httpGet.setConfig(requestConfig)
    val httpResponse = httpClient.execute(httpGet)
    val httpEntity = httpResponse.getEntity
    val contentType = httpEntity.getContentType.getElements.mkString.toUpperCase.replace(" ","")
    var charset = ""
    var htmlContent = ""
    if (contentType.contains("CHARSET=")) {
      contentType.split(";").foreach(s => {
        if (s.contains("CHARSET=")) charset = s.replace("CHARSET=","")
      })
    }
    if (charset != "") {
      htmlContent = IOUtils.toString(httpEntity.getContent, charset)
    } else {
      val (is1, is2) = inputStreamCopy(httpEntity.getContent)
      val tmpStr = IOUtils.toString(is1, "UTF-8").toLowerCase
      val doc = Jsoup.parse(tmpStr)
      if (doc.select("meta") != null) {
        val iter = doc.select("meta").iterator()
        while (iter.hasNext) {
          val metaStr = iter.next().toString
          if (metaStr.contains("charset")) {
            val s1 = metaStr.substring(metaStr.indexOf("charset")).replace(" ","")
            if (s1.contains("charset=\"")) {
              val s2 = s1.replace("charset=\"","")
              charset = s2.substring(0,s2.indexOf("\"")).toUpperCase
            } else {
              val s2 = s1.replace("charset=","")
              charset = s2.substring(0,s2.indexOf("\"")).toUpperCase
            }
          }
        }
      }
      if (charset == "") {
        charset = "UTF-8"
      }
      htmlContent = IOUtils.toString(is2, charset)
    }
    htmlContent
  }

  var url = ""
  var htmlContent = ""
  var charset = ""
  var doc: Document = null
  var json: JsValue = null
  var pattern = ""
  var headers = Array(
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/50.0.2661.75 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10; rv:33.0) Gecko/20100101 Firefox/33.0",
    "Mozilla/5.0 (compatible; MSIE 10.0; Windows NT 6.1; Trident/6.0)",
    "Mozilla/5.0 (Windows; U; MSIE 9.0; Windows NT 9.0; en-US)",
    "Mozilla/5.0 (compatible; MSIE 9.0; Windows NT 6.1; Trident/5.0; FunWebProducts)",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_9_3) AppleWebKit/537.75.14 (KHTML, like Gecko) Version/7.0.3 Safari/7046A194A",
    "Mozilla/5.0 (iPad; CPU OS 6_0 like Mac OS X) AppleWebKit/536.26 (KHTML, like Gecko) Version/6.0 Mobile/10A5355d Safari/8536.25",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_7_3) AppleWebKit/534.55.3 (KHTML, like Gecko) Version/5.1.3 Safari/534.53.10",
    "Opera/9.80 (Windows NT 6.0) Presto/2.12.388 Version/12.14"
  )
  var header = headers(Random.nextInt(headers.length))

  if (args.length == 1) {
    url = args(0)

    //json内容抽取
    url = "http://www.leikeji.com/columns/getArticleList?ifHome=1&status=1&channels=1&pageIndex=3&pageSize=10&orderBy=postDate&orderType=desc&colName=%E6%96%B0%E9%97%BB"
    htmlContent = Jsoup.connect(url).ignoreContentType(true).execute().body()
    json = Json.parse(htmlContent)
    (json \ "data" \\ "appweburl").foreach(js => {
      println(js.toString())
    })

    //html内容抽取
    url = "http://www.leikeji.com/article/5373"
    header = headers(Random.nextInt(headers.length))
    doc = Jsoup.connect(url).header("User-Agent",header).timeout(10*1000).get()
    pattern = "div[class=article-body]"
    doc.select(pattern).foreach(article => {
      println(article)
    })

    //html内容抽取,图片下载
    url = "http://www.ifanr.com/638074"
    header = headers(Random.nextInt(headers.length))
    doc = Jsoup.connect(url).header("User-Agent",header).timeout(10*1000).get()
    pattern = "article"
    //标题抽取
    println("title: "+doc.select("h1.c-single-normal__title").first().text())
    doc.select(pattern).foreach(article => {
      //内容抽取
      println(article.text())
      println("####################")
      article.select("img").foreach(img => {
        //图片抽取
        val imgSrc = img.attr("abs:src")
        println(imgSrc)
        //图片下载
        val imgResp = Jsoup.connect(imgSrc).ignoreContentType(true).execute().bodyAsBytes()
        val file = new FileOutputStream(imgSrc.replace(":","").replace("/",""))
        println(s"$imgSrc downloaded")
        file.write(imgResp)
        file.close()
      })
    })

    //html列表抽取
    url = "http://www.themakers.cn/index/index-1.html"
    header = headers(Random.nextInt(headers.length))
    doc = Jsoup.connect(url).header("User-Agent",header).timeout(10*1000).get()
    pattern = "div#listbox"
    doc.select(pattern).foreach(div => {
      //链接抽取
      div.select("div.pro-title > a[href^=/store/detail-]").foreach(a => {
        val href = a.attr("abs:href")
        println("a href: "+href)
      })
      div.select("img").foreach(img => {
        println("img: "+img.attr("abs:src"))
      })
    })

    //微信内容页抽取
    url = "http://mp.weixin.qq.com/s?src=3&timestamp=1461227773&ver=1&signature=CiQkznJRsrvadQct3RUIwf8vdtUpGzUD2qjAoB1fZpoMaZL3ZWC0sqmNbFL1a0bWEVuT7ObSAbdp-0Uh1INIBuEmgHOcQKHR3eFWVsMi-XfkdvULJG6a1drLJ4FACsiighUHcV32VRimHqv03hF2OxRD8DlGbCtM*qowQUnUmt4="
    val firefoxProfile = new FirefoxProfile
    firefoxProfile.setPreference("permissions.default.image", 2)
//    firefoxProfile.setPreference("permissions.default.script", 2)
    firefoxProfile.setPreference("plugin.state.flash", 0)
    firefoxProfile.setPreference("permissions.default.stylesheet", 2)
    firefoxProfile.setPreference("permissions.default.subdocument", 2)
    val driver = new FirefoxDriver(firefoxProfile)
    driver.manage().window().setSize(new Dimension(800, 640))
    driver.get(url)
    val scroll = driver.findElement(By.tagName("body"))
    val screenHeight = 500
    var currentScroll = 0
    var pageHeight = scroll.getSize.getHeight
    Thread.sleep(1000)
    //向浏览器注入Javascript隐藏无用信息
    val jse = driver.asInstanceOf[JavascriptExecutor]
    jse.executeScript(
      """
        var element1 = document.getElementById("sg_tj");
        element1.parentNode.removeChild(element1);
        var element2 = document.getElementsByClassName("rich_media_area_extra");
        var i;
        for (i = 0; i < element2.length; i++) {
            element2[i].parentNode.removeChild(element2[i]);
        }
      """)
    while (currentScroll < pageHeight) {
      Thread.sleep(500)
      scroll.sendKeys(Keys.PAGE_DOWN)
      currentScroll += screenHeight
      pageHeight = scroll.getSize.getHeight
    }
    htmlContent = driver.getPageSource
    doc = Jsoup.parse(htmlContent, url)
    //图片抽取
    doc.select("img").foreach(img => {
      if (img.attr("src").startsWith("http://") || img.attr("src").startsWith("https://")) {
        println(img.attr("src"))
      }
    })
    //内容区抽取
    doc.select("#img-content").foreach(body => {
      println(body)
    })
    driver.quit()

  } else {
    println("Usage: demo.HttpComponentDemo <url>")
  }
}
