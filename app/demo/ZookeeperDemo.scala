package demo

import org.apache.log4j.{Level, Logger}
import org.apache.zookeeper._

/**
  * Created by cookeem on 16/3/29.
  */
object ZookeeperDemo extends App {
  class Executor extends Watcher {
    val logger: Logger = Logger.getLogger(classOf[ClientCnxn])
    logger.setLevel(Level.OFF)
    val zk = new ZooKeeper("localhost:2181", 2000, this)

    def process(event: WatchedEvent) = {
      println(s"### event: ${event.getState}")
    }
  }

  val executor = new Executor
  val zk = executor.zk
  val children = zk.getChildren("/", true)
  val bytes = zk.getData("/clusterstate.json", executor, null)
  val str = new String(bytes, "UTF-8")
  zk.close()
}
