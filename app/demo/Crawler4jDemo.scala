package demo

import java.io.File

import com.google.common.io.Files
import edu.uci.ics.crawler4j.crawler.{Page, WebCrawler, CrawlController, CrawlConfig}
import edu.uci.ics.crawler4j.fetcher.PageFetcher
import edu.uci.ics.crawler4j.parser.HtmlParseData
import edu.uci.ics.crawler4j.robotstxt.{RobotstxtServer, RobotstxtConfig}
import edu.uci.ics.crawler4j.url.WebURL

/**
  * Created by cookeem on 16/3/30.
  */
object Crawler4jDemo extends App {
  val crawlStorageFolder = "zzz"
  val numberOfCrawlers = 7
  val config = new CrawlConfig()
  config.setCrawlStorageFolder(crawlStorageFolder)

  val pageFetcher = new PageFetcher(config)
  val robotstxtConfig = new RobotstxtConfig()
  val robotstxtServer = new RobotstxtServer(robotstxtConfig, pageFetcher)
  val controller = new CrawlController(config, pageFetcher, robotstxtServer)

  controller.addSeed("http://www.ics.uci.edu/~lopes/")
  controller.addSeed("http://www.ics.uci.edu/~welling/")
  controller.addSeed("http://www.ics.uci.edu/")

  controller.start(classOf[MyCrawler], numberOfCrawlers)

  class MyCrawler extends WebCrawler {
    override def shouldVisit(referringPage: Page, url: WebURL): Boolean = {
      val filters = ".*(\\.(css|js|gif|mp3|mp3|zip|gz))$".r
      val href = url.getURL().toLowerCase()
      filters.findFirstIn(href) == None
    }

    override def visit(page: Page) = {
      val url = page.getWebURL().getURL()
      println("URL: " + url)
      if (page.getParseData().isInstanceOf[HtmlParseData]) {
        val htmlParseData = page.getParseData().asInstanceOf[HtmlParseData]
        val text = htmlParseData.getText()
        val title = htmlParseData.getTitle
        val html = htmlParseData.getHtml()
        val links = htmlParseData.getOutgoingUrls()

        println(s"### Text title: $title")
        println("### Text length: " + text.length())
        println("### Html length: " + html.length())
        println("### Number of outgoing links: " + links.size())
      } else {
        val storageFolder = new File(crawlStorageFolder)
        if (!storageFolder.exists()) {
          storageFolder.mkdirs()
        }
        val filename = storageFolder.getAbsolutePath() + "/" + url.split("/")(url.split("/").length - 1)
        Files.write(page.getContentData(), new File(filename))
        println(s"### File save to $filename")
      }
    }
  }
}
