package demo

import java.io.File
import java.util.Properties

import com.jcraft.jsch.{ChannelSftp, JSch}
import org.apache.commons.io.FileUtils

/**
  * Created by cookeem on 16/7/19.
  */
object SFTPTest1 extends App {
  val serverAddress = "localhost"
  val userId = "cookeem"
  val password = "man8080"
  val remoteDirectory = "/Volumes/Share/Download/remoteDir/"
  val localDirectory = "/Volumes/Share/Download/localDir/"
  val port = 22
  val fileToFTP = "qq.png"
  val filepath = s"$localDirectory$fileToFTP"
  val jsch = new JSch()
  val session = jsch.getSession(userId, serverAddress, port)
  session.setPassword(password)
  val config = new Properties()
  config.put("StrictHostKeyChecking", "no")
  session.setConfig(config)
  session.connect()
  val channel = session.openChannel("sftp").asInstanceOf[ChannelSftp]
  channel.connect()
  //上传
  channel.cd(remoteDirectory)
  channel.put(filepath, fileToFTP)

  //下载
  val fileToDownload = "a.html"
  val is = channel.get(s"$remoteDirectory$fileToDownload")
  val filepath2 = s"$localDirectory$fileToDownload"
  FileUtils.copyInputStreamToFile(is, new File(filepath2))

  //目录操作
  channel.cd("/Users/cookeem/Downloads")
  channel.mkdir("test")
  channel.rmdir("/Users/cookeem/Downloads/test")

  channel.exit()
  channel.disconnect()
  session.disconnect()
}
