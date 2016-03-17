package actor

import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata.{ORSet, ORSetKey, DistributedData}
import com.typesafe.config.ConfigFactory

import java.util.concurrent.ThreadLocalRandom

import scala.concurrent.duration._

/**
 * Created by cookeem on 16/3/8.
 */

object DistributedDataTest1 extends App {
  object DataBot {
    case object Tick
  }

  class DataBot extends Actor with ActorLogging {
    import DataBot._
    val replicator = DistributedData(context.system).replicator
    implicit val node = Cluster(context.system)
    import context.dispatcher
    val tickTask = context.system.scheduler.schedule(5.seconds, 5.seconds, self, Tick)
    println(s"val tickTask = context.system.scheduler.schedule(2.seconds, 2.seconds, $self, Tick)")
    val DataKey = ORSetKey[String]("key")
    replicator ! Subscribe(DataKey, self)
    println(s"replicator ${replicator}")
    self ! Tick

    def receive = {
      case Tick =>
        val s = ThreadLocalRandom.current().nextInt(97, 123).toChar.toString
        val mode = ThreadLocalRandom.current().nextBoolean()
        println(s"$self receive Tick! $s $mode")
        if (mode) {
          // add
          println(s"$self Adding: $s")
          replicator ! Update(key = DataKey, initial = ORSet.empty[String], writeConsistency = WriteLocal)(_ + s)
        } else {
          // remove
          println(s"$self Removing: $s", s)
          replicator ! Update(key = DataKey, initial = ORSet.empty[String], writeConsistency = WriteLocal)(_ - s)
        }
      case c: UpdateResponse[_] =>
        println(s"$self receive UpdateResponse: $c")
      case c @ Changed(DataKey) =>
        val data = c.get(DataKey)
        println(s"$self Current elements: ${data.elements}")
    }
    override def postStop(): Unit = tickTask.cancel()
  }

  val configStr = """
      akka {
        loglevel = "INFO"
        log-dead-letters = off
        actor {
          provider = "akka.cluster.ClusterActorRefProvider"
        }
        remote {
          log-remote-lifecycle-events = off
          netty.tcp {
            hostname = "localhost"
          }
        }
        cluster {
          #监控集群管理事件,并放到INFO中
          log-info = on
          seed-nodes = [
          "akka.tcp://cluster@localhost:2551",
          "akka.tcp://cluster@localhost:2552"]
          auto-down-unreachable-after = 10s
          metrics.enabled = off
        }
        extensions = ["akka.cluster.metrics.ClusterMetricsExtension"]
      }
                  """
  println("############# start cluster ##############")
  val ports = Array("2551","2552")
  ports.foreach(port => {
    val config = ConfigFactory.parseString(configStr)
      .withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port"))
    val system = ActorSystem("cluster", config)
    val actor = system.actorOf(Props[DataBot], "databot")
  })
}
