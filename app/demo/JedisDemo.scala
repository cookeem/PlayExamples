package demo

import redis.clients.jedis.{JedisPool, JedisPoolConfig, Jedis}
import scala.collection.JavaConversions._

/**
  * Created by cookeem on 16/3/23.
  */
object JedisDemo extends App {
  val config = new JedisPoolConfig()
  config.setMaxTotal(200)
  config.setMaxIdle(100)
  config.setTestOnBorrow(true)
  val jedisPool = new JedisPool(config, "localhost", 6379, 1000)
  val jedisByPool: Jedis = jedisPool.getResource

  val jedis: Jedis = new Jedis("localhost", 6379)

  //字符串测试
  val strName = "mystr"
  jedis.set(strName, "cookeem")  //新建或者更新字符串
  println(jedis.get(strName))    //获取字符串
  jedis.append(strName, " is a cool man")  //字符串追加
  println(jedis.get(strName))
  jedis.del(strName)             //删除字符串
  println(jedis.get(strName))
  jedis.mset("name","cookeem","age","23","qq","476777XXX")  //同时设置多个字符串,相当于多个键值对
  jedis.incr("age")             //进行+1操作
  jedis.decr("age")             //进行-1操作
  println(s"${jedis.get("name")} - ${jedis.get("age")} - ${jedis.get("qq")}")
  jedis.`type`("name")          //获取对象的类型

  //map测试
  val mapName = "mymap"
  jedis.hmset(mapName, Map("name"->"faith", "age"->"30", "qq"->"1123123")) //新建hmset
  jedis.hmget(mapName, "name", "age", "qq").foreach(println)               //获取hmset
  jedis.hdel(mapName,"age")      //删除hmset中某个键
  jedis.hmget(mapName, "age").foreach(println)
  jedis.hlen(mapName)            //获取hmset中的字段数
  jedis.exists(mapName)          //检查对象是否存在
  jedis.hkeys(mapName)           //返回hmset的所有keys
  jedis.hvals(mapName)           //返回hmset的所有value
  jedis.hkeys(mapName).map(k =>
    s"$k -> ${jedis.hmget(mapName, k)}"
  ).mkString(",")

  //list测试,list中的item可以重复
  val listName = "mylist"
  jedis.del(listName) //删除对象
  jedis.lpush(listName,"spring")    //往list中追加数据,追加到头部
  jedis.lpush(listName,"struts")
  jedis.lpush(listName,"hibernate")
  jedis.lrange(listName, 0,-1).foreach(println) //获取list信息
  jedis.del(listName)
  jedis.rpush(listName,"spring")    //往list中追加数据,追加到尾部
  jedis.rpush(listName,"struts")
  jedis.rpush(listName,"hibernate")
  jedis.sort(listName)
  jedis.lrange(listName, 0,-1).foreach(println) //获取list信息

  //set测试,set中的item不可以重复
  val setName = "myset"
  jedis.sadd(setName,"haijian") //往set中追加数据
  jedis.sadd(setName,"wenjing")
  jedis.sadd(setName,"qingqing")
  jedis.sadd(setName,"曾海剑")
  jedis.sadd(setName,"temp")
  jedis.srem(setName,"temp")    //从set中移除数据
  jedis.smembers(setName).foreach(println)  //获取set的所有数据
  jedis.sismember(setName, "who")   //检查set中是否存在对应数据
  jedis.srandmember(setName)        //获取set中最后一个数据
  jedis.scard(setName)              //获取set中的元素个数

  //List排序,元素必须是Int或者Double
  val slName = "mysortlist"
  jedis.rpush(slName, "1")
  jedis.lpush(slName,"6")
  jedis.lpush(slName,"3")
  jedis.lpush(slName,"9")
  jedis.sort(slName)


}
