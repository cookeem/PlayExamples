package actor

import java.util.concurrent.ThreadLocalRandom

import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ddata._
import akka.cluster.ddata.Replicator._
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

/**
 * Created by cookeem on 16/3/12.
 */
object DistributedDataTest2 extends App {
  class DDActor extends Actor with ActorLogging {
    val replicator = DistributedData(context.system).replicator
    implicit val node = Cluster(context.system)
    val Counter1Key = PNCounterKey("counter1")
    val Set1Key = GSetKey[String]("set1")
    val Set2Key = ORSetKey[String]("set2")
    val ActiveFlagKey = FlagKey("active")

    replicator ! Update(Counter1Key, PNCounter(), WriteLocal)(_ + 1)
    val writeTo3 = WriteTo(n = 3, timeout = 1.second)
    replicator ! Update(Set1Key, GSet.empty[String], writeTo3)(_ + "hello")
    val writeMajority = WriteMajority(timeout = 5.seconds)
    replicator ! Update(Set2Key, ORSet.empty[String], writeMajority)(_ + "hello")
    val writeAll = WriteAll(timeout = 5.seconds)
    replicator ! Update(ActiveFlagKey, Flag.empty, writeAll)(_.switchOn)

    def receive = {
      case UpdateSuccess(Counter1Key, req) =>
        println(s"UpdateSuccess(Counter1Key:$Counter1Key, $req)")
      case UpdateSuccess(Set1Key, req) =>
        println(s"UpdateSuccess(Set1Key:$Set1Key, $req)")
      case UpdateTimeout(Set1Key, req) =>
        println(s"UpdateTimeout(Set1Key:$Set1Key, $req)")
    }
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
          log-info = off
          seed-nodes = [
          "akka.tcp://cluster@localhost:2551",
          "akka.tcp://cluster@localhost:2552",
          "akka.tcp://cluster@localhost:2553"]
          auto-down-unreachable-after = 10s
          metrics.enabled = off
        }
        extensions = ["akka.cluster.metrics.ClusterMetricsExtension"]
      }
                  """
  println("############# start cluster ##############")
  val ports = Array("2551","2552","2553")
  ports.foreach(port => {
    val config = ConfigFactory.parseString(configStr)
      .withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port"))
    val system = ActorSystem("cluster", config)
    val actor = system.actorOf(Props[DDActor], "actor")
  })
}
