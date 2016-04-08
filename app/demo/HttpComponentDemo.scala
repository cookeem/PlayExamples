package demo

import org.apache.commons.io.IOUtils
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils

/**
  * Created by cookeem on 16/3/30.
  */
object HttpComponentDemo extends App {
  val url = "https://weibo.com"
  val httpClient = HttpClients.createDefault()
  val requestConfig = RequestConfig.custom()
    .setConnectTimeout(2000).setConnectionRequestTimeout(2000)
    .setSocketTimeout(2000).build()
  val httpGet: HttpGet = new HttpGet(url)
  httpGet.setConfig(requestConfig)
  val httpResponse = httpClient.execute(httpGet)
  val httpEntity = httpResponse.getEntity
  val contentType = httpEntity.getContentType
  val is = httpEntity.getContent
  IOUtils.toString(is, "UTF-8")
  val htmlContent = EntityUtils.toString(httpEntity, "UTF-8")

}
