package demo

import java.io.File

import org.apache.commons.io.FileUtils
import org.openqa.selenium.{OutputType, TakesScreenshot, Dimension}
import org.openqa.selenium.firefox.FirefoxDriver

/**
  * Created by cookeem on 16/3/21.
  */

// CentOS firefox和Xvfb安装指引
//# 安装Xvfb虚拟X Windows和
//$ yum install Xvfb libXfont Xorg
//
//# 安装firefox
//$ yum install firefox
//
//# 安装中文字体支持
//$ yum -y groupinstall chinese-support
//
//# 启动Xvfb虚拟屏幕，并设置环境变量
//$ nohup /usr/bin/Xvfb :2 -screen 0 1024x768x16 >> /dev/null &
//$ export DISPLAY=:2


object FirefoxDriverDemo extends App {
  println("Usage: example.FirefoxDriverDemo <url> <fileName>")
  var url = ""
  var fileName = ""
  if (args.length == 2) {
    url = args(0)
    fileName = args(1)
    val cwd = System.getProperty("user.dir")
    val driver = new FirefoxDriver()
    driver.manage().window().setSize(new Dimension(480, 720))
    driver.get(url)
    val imgFile = driver.asInstanceOf[TakesScreenshot].getScreenshotAs(OutputType.FILE)
    FileUtils.copyFile(imgFile, new File(s"$cwd/$fileName"))
    driver.quit()
  }
}
