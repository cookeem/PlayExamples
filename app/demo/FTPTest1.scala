package demo

import java.io.{FileOutputStream, FileInputStream, File}

import org.apache.commons.net.ftp.{FTP, FTPClient, FTPHTTPClient}

import scala.collection.JavaConversions._

/**
  * Created by cookeem on 16/7/19.
  */
object FTPTest1 extends App {
  val proxyHost = ""
  val proxyPort = 0
  val proxyUser = ""
  val proxyPass = ""
  val server = "localhost"
  val port = 21
  val user = "cookeem"
  val password = "man8080"
  var ftpClient: FTPClient = null
  if (proxyHost == "") {
    ftpClient = new FTPClient()
  } else {
    if (proxyUser == "") {
      ftpClient = new FTPHTTPClient(proxyHost, proxyPort)
    } else {
      ftpClient = new FTPHTTPClient(proxyHost, proxyPort, proxyUser, proxyPass)
    }
  }
  //连接
  ftpClient.connect(server, port)
  ftpClient.login(user, password)

  //设置
  ftpClient.enterLocalPassiveMode()
  ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
  ftpClient.setControlKeepAliveTimeout(30L)

  //上传
  val filename = "NOTICE"
  val localPath = "/Users/cookeem/Downloads/local/"
  val remotePath = "/Users/cookeem/Downloads/remote/"
  val is = new FileInputStream(new File(s"$localPath$filename"))
  ftpClient.storeFile(s"$remotePath$filename", is)
  is.close()

  //下载
  val filename2 = "LICENSE"
  val fos = new FileOutputStream(s"$localPath$filename2")
  ftpClient.setBufferSize(1024)
  ftpClient.retrieveFile(s"$remotePath$filename2", fos)
  fos.close()

  //目录操作
  ftpClient.changeToParentDirectory()
  ftpClient.getReplyString
  ftpClient.changeWorkingDirectory("/Users/cookeem/Downloads")
  ftpClient.getReplyString
  ftpClient.listDirectories().foreach{f => println(f)}
  ftpClient.getReplyString
  ftpClient.listFiles("/").foreach(println)
  ftpClient.getReplyString
  ftpClient.mlistDir("/").foreach(println)
  ftpClient.getReplyString
  ftpClient.makeDirectory(remotePath + "newdir")
  ftpClient.getReplyString
  ftpClient.removeDirectory(remotePath + "newdir")
  ftpClient.getReplyString

  //退出
  ftpClient.logout()
  ftpClient.disconnect()
}
