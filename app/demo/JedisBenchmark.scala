package demo

import java.util.Random

import redis.clients.jedis._
import scala.collection.JavaConversions._

/**
  * Created by cookeem on 16/7/11.
  */
object JedisBenchmark extends App {
  val config = new JedisPoolConfig()
  config.setMaxTotal(200)
  config.setMaxIdle(100)
  config.setTestOnBorrow(true)
  val jedisPool = new JedisPool(config, "localhost", 6379, 1000)
  val jedisByPool: Jedis = jedisPool.getResource

  val jedis: Jedis = new Jedis("localhost", 6379)

  //测试redis插入性能,100000一个batch提交,测试总量1亿个记录
  var begin = System.currentTimeMillis()
  var start = System.currentTimeMillis()
  var count = 0
  var buffer = Seq[Int]()
  var t = jedis.multi()
  (1 to 100000000).toStream.foreach{ i =>
    val end = System.currentTimeMillis()
    if (end - start > 1000) {
      println(s"every sec insert count: $count, current num: $i")
      count = 0
      start = end
    }
    if (i % 100000 == 1) {
      t = jedis.multi()
      t.set(s"k$i", s"v$i")
    } else if (i % 100000 == 0) {
      t.set(s"k$i", s"v$i")
      t.exec()
      println(s"transaction exec")
    } else {
      t.set(s"k$i", s"v$i")
    }
    count += 1
  }
  val duration = (System.currentTimeMillis() - begin) / 1000
  println(s"# duration: $duration")

  //测试1亿个记录的get性能,读取10万个记录消耗的时长
  val r = new Random()
  val s = System.currentTimeMillis
  (1 to 100000).toStream.foreach{i =>
    val k = r.nextInt(100000000)
    jedis.get(s"k$k")
  }
  val dur = System.currentTimeMillis - s
  println(s"dur: $dur")

  //测试通配符查找keys
}
