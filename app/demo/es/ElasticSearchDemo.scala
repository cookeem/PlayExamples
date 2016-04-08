package demo.es

import java.net.InetAddress
import java.util.Date

import org.elasticsearch.action.bulk.{BackoffPolicy, BulkProcessor, BulkRequest, BulkResponse}
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.get.{GetRequest, MultiGetRequest}
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.termvectors.TermVectorsRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.common.unit.{ByteSizeUnit, ByteSizeValue, TimeValue}
import org.elasticsearch.common.xcontent.{ToXContent, XContentType, XContentFactory}

import scala.collection.JavaConversions._
/**
  * Created by cookeem on 16/3/31.
  */
object ElasticSearchDemo extends App {
  //创建client
  val settings = Settings
    .settingsBuilder()
    .put("cluster.name", "my_cluster_name").build()
  val client = TransportClient
    .builder()
    .settings(settings)
    .build()
    .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName("localhost"), 9300))
    .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName("localhost"), 9301))

  //构造json
  val builder = XContentFactory.jsonBuilder().startObject()
    .field("user", "kimchy")
    .field("postDate", new Date())
    .field("message", "trying out Elasticsearch")
    .endObject()
  val json = builder.string()

  //索引文档index
  val indexResponse = client.prepareIndex("twitter", "tweet", "1").setSource(builder).get()
  indexResponse.toString
  indexResponse.getType
  //创建第二个文档
  client.prepareIndex("twitter", "tweet", "2").setSource(
    XContentFactory.jsonBuilder().startObject()
      .field("user", "haijian")
      .field("postDate", new Date())
      .field("message", "trying out Solr!")
      .endObject()
  ).get()
  //以IndexRequest方式创建
  val indexRequest = new IndexRequest("myindex", "index", "1")
        .source(
          XContentFactory.jsonBuilder().startObject()
            .field("name", "haijian")
            .field("age", 30)
            .field("job" , "it")
            .endObject()
        )
  client.index(indexRequest).get()

  //获取文档get
  //setOperationThreaded假如设置成true,response的结果将会输出到其他线程,默认为true
  val getResponse = client.prepareGet("twitter", "tweet", "1").setOperationThreaded(true).get()
  getResponse.getSourceAsString
  //以GetRequest方式获取
  val getRequest = new GetRequest("twitter", "tweet", "2")
  client.get(getRequest).get().getSourceAsString

  //删除文档delete
  val deleteResponse = client.prepareDelete("twitter", "tweet", "1").get()
  deleteResponse.isFound

  //更新文档Update
  val updateResponse = client.prepareUpdate("myindex", "index", "1").setDoc(
    XContentFactory.jsonBuilder().startObject()
      .field("age", 37)
      .endObject()
  ).get()
  client.prepareGet("myindex", "index", "1").setOperationThreaded(true).get().getSourceAsString
  //以UpdateRequest方式更新
  val indexRequest2 = new IndexRequest("myindex", "index", "2")
    .source(XContentFactory.jsonBuilder()
      .startObject()
      .field("name", "wenjing")
      .field("age", 30)
      .field("job" , "market")
      .endObject())
  val updateRequest = new UpdateRequest("myindex", "index", "2")
    .doc(
      XContentFactory.jsonBuilder()
        .startObject()
        .field("age", 31)
        .endObject()
    )
    .upsert(indexRequest2)
  client.update(updateRequest).get()
  client.prepareGet("myindex", "index", "2").setOperationThreaded(true).get().getSourceAsString

  //获取多个文档MultiGet
  val multiGetResponse = client.prepareMultiGet()
    .add("twitter", "tweet", "1")
    .add("twitter", "tweet", "2")
    .add("myindex", "index", "1", "2")
    .get()
  multiGetResponse.getResponses.foreach(resp => println(resp.getResponse.getSourceAsString))
  //以MultiGetRequest方式获取
  val multiGetRequest = new MultiGetRequest()
    .add(new MultiGetRequest.Item("twitter", "tweet", "1"))
    .add("myindex", "index", "1")
  val multiGetIter = client.multiGet(multiGetRequest).get().iterator()
  while (multiGetIter.hasNext) {
    println(multiGetIter.next().getResponse.getSourceAsString)
  }

  //批量操作Bulk,可以混合添加各种操作
  val bulkRequest = client.prepareBulk()
    .add(client.prepareIndex("myindex", "index", "3").setSource(
      XContentFactory.jsonBuilder()
        .startObject()
        .field("name", "qingqing")
        .field("age", 5)
        .field("school", "changgang")
        .endObject()
    ))
    .add(client.prepareIndex("twitter", "tweet", "3").setSource(
      XContentFactory.jsonBuilder()
        .startObject()
        .field("user", "faith")
        .field("postDate", new Date())
        .field("message", "get off work!")
        .endObject()
    ))
    //script更新
    .add(client.prepareUpdate("myindex", "index", "1").setSource(
      XContentFactory.jsonBuilder()
        .startObject()
          .startObject("script")
            .field("inline", "ctx._source.age += count")
            .startObject("params")
              .field("count", 100)
            .endObject()
          .endObject()
        .endObject()
      )
    )
  bulkRequest.get()
  client.prepareMultiGet()
    .add("twitter", "tweet", "3")
    .add("myindex", "index", "3")
    .get().getResponses.foreach(resp => println(resp.getResponse.getSourceAsString))

  //BulkProcessor批处理器
  val bulkListener = new BulkProcessor.Listener() {
    override def beforeBulk(executionId: Long, request: BulkRequest) = {
      println(s"### Begin to BulkRequest, ${request.requests().map(t => t.getDescription).mkString(",")}")
    }
    override def afterBulk(executionId: Long, request: BulkRequest, response: BulkResponse) = {
      println(s"### Finish to BulkRequest Success, ${request.requests().map(t => t.getDescription).mkString(",")}, ${response.getItems.mkString(",")}")
    }
    override def afterBulk(executionId: Long, request: BulkRequest, failure: Throwable) = {
      println(s"### Finish to BulkRequest Failure, ${request.requests().map(t => t.getDescription).mkString(",")}, ${failure.getMessage}")
    }
  }
  val bulkProcessor = BulkProcessor.builder(client, bulkListener)
    .setBulkActions(10000)
    .setBulkSize(new ByteSizeValue(1, ByteSizeUnit.GB))
    .setFlushInterval(TimeValue.timeValueSeconds(5))
    .setConcurrentRequests(1)
    .setBackoffPolicy(
      BackoffPolicy.exponentialBackoff(TimeValue.timeValueMillis(100), 3))
    .build()

  bulkProcessor
    .add(
      new IndexRequest("twitter", "tweet", "4").source(
        XContentFactory.jsonBuilder()
          .startObject()
          .field("user", "cookeem")
          .field("postDate", new Date())
          .field("message", "i am hero!")
          .endObject()
      )
    )
    .add(
      new UpdateRequest("twitter", "tweet", "4").doc(
        XContentFactory.jsonBuilder()
          .startObject()
          .field("message", "# i am hero! #")
          .endObject()
      )
    )
    //自增长ID
    .add(new IndexRequest("twitter", "tweet").source(
      XContentFactory.jsonBuilder()
          .startObject()
          .field("user", "zenghaijian")
          .field("postDate", new Date())
          .field("message", "i am super hero!")
          .endObject()
      )
    )
  client.prepareGet("twitter", "tweet", "4").setOperationThreaded(true).get().getSourceAsString
  bulkProcessor.add(new DeleteRequest().index("twitter").`type`("tweet").id("4"))
  bulkProcessor.close()

  //Term Vector词向量
  val tvResponse = client.prepareTermVectors("twitter", "tweet", "1").setDfs(true).setSelectedFields("message").setOffsets(true).setPayloads(true).setTermStatistics(true).setFieldStatistics(true).get()
  //以TermVectorsRequest方式获取
  val tvRequest = new TermVectorsRequest("myindex", "index", "1").dfs(true).selectedFields("message").offsets(true).payloads(true).termStatistics(true).fieldStatistics(true)
  val tvResponse2 = client.termVectors(tvRequest).get()
  //把response转换成toXContent
  val xcBuilder = XContentFactory.contentBuilder(XContentType.JSON).prettyPrint().startObject()
  val tvString = tvResponse.toXContent(xcBuilder, ToXContent.EMPTY_PARAMS).string()
  //Multi Term Vector
  client.prepareMultiTermVectors()
    .add("twitter", "tweet", "2", "3")
    .add(new TermVectorsRequest("myindex", "index", "1").dfs(true).selectedFields("message").offsets(true).payloads(true).termStatistics(true).fieldStatistics(true))
    .get
    .getResponses.foreach(resp => {
    val builder = XContentFactory.contentBuilder(XContentType.JSON).prettyPrint().startObject()
    println(resp.getResponse.toXContent(builder, ToXContent.EMPTY_PARAMS).string())
  })
}
