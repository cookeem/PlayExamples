package demo.es

import java.net.InetAddress

import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.common.xcontent.XContentFactory

/**
  * Created by cookeem on 16/4/1.
  */
object ElasticSearchDemo2 extends App {
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

  /* 相当于:
    $ curl -XPUT 'http://localhost:9200/website/' -d '{
      "settings" : {
          "number_of_shards" : 2,
          "number_of_replicas" : 2,
          "analysis" : {
            "analyzer" : "index_ansj"
          }
      },
      "mappings" : {
          "data" : {
              "properties" : {
                  "title" : { "type" : "string", "index" : "index_ansj" },
                  "content" : { "type" : "string", "index" : "index_ansj" }
              }
          }
      }
    }'
  */
  val indexMapping = client.admin().indices().prepareCreate("website")
    .setSettings(
      XContentFactory.jsonBuilder()
        .startObject()
          .field("number_of_shards", "2")
          .field("number_of_replicas", "2")
          .startObject("analysis")
            .field("analyzer","index_ansj")
          .endObject()
        .endObject()
    )
    .addMapping("data",
      XContentFactory.jsonBuilder()
        .startObject()
          .startObject("data")
            .startObject("properties")
              .startObject("title")
                .field("type", "string")
                .field("index", "not_analyzed")
              .endObject()
              .startObject("content")
                .field("type","string")
              .endObject()
            .endObject()
          .endObject()
        .endObject()
    )
  val indexMapping2 = client.admin().indices().prepareCreate("website")
    .setSettings(
      """
          "settings" : {
              "number_of_shards" : 2,
              "number_of_replicas" : 2,
              "analysis" : {
                "analyzer" : "index_ansj"
              }
          },
          "mappings" : {
              "data" : {
                  "properties" : {
                      "title" : { "type" : "string", "index" : "index_ansj" },
                      "content" : { "type" : "string", "index" : "index_ansj" }
                  }
              }
          }
        }      """
    )
  val indexMappingResponse = indexMapping.execute().actionGet()
  client.admin().indices().prepareGetIndex().addIndices("website").execute().actionGet()
  client.admin().indices().prepareDelete("website").execute().actionGet()
}
