package actor

import akka.actor._
import akka.cluster._
import akka.cluster.ClusterEvent._
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

/**
 * Created by cookeem on 16/1/1.
 * Cluster事件基础
 */
class SimpleClusterListener extends Actor with ActorLogging {
  val cluster = Cluster(context.system)
  // subscribe to cluster changes, re-subscribe when restart
  override def preStart(): Unit = {
    //#subscribe
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents, classOf[MemberEvent], classOf[UnreachableMember])
  }
  override def postStop(): Unit = {
    cluster.unsubscribe(self)
  }

  def receive = {
    case MemberUp(member) =>
      log.info(s"Member is Up: ${member.address}")
    case LeaderChanged(addr) =>
      log.info(s"Leader changed: ${addr}")
    case UnreachableMember(member) =>
      log.warning(s"Member Unreachable: ${member.address}")
    case MemberRemoved(member, previousStatus) =>
      log.warning(s"Member is Removed: ${member.address} after $previousStatus")
    case MemberExited(member) =>
      log.warning(s"Member is Exited: ${member.address}")
    case "stop" =>
      cluster.down(cluster.selfAddress)
      //context.system.terminate()
      log.info(s"Member is stop! ${cluster.selfAddress}")
    case evt: MemberEvent => // ignore
      log.info(s"Memver event ${evt.member.status} ${evt.member.address}")
  }

  //定义集群member removed关闭事件
  cluster.registerOnMemberRemoved{
    // exit JVM when ActorSystem has been terminated
    log.warning(s"cluster.system.registerOnTermination(System.exit(0))")
    cluster.system.registerOnTermination(System.exit(0))
    cluster.system.terminate()
    // In case ActorSystem shutdown takes longer than 10 seconds,
    // exit the JVM forcefully anyway.
    // We must spawn a separate thread to not block current thread,
    // since that would have blocked the shutdown of the ActorSystem.
    new Thread {
      override def run(): Unit = {
        if (Try(Await.ready(cluster.system.whenTerminated, 10.seconds)).isFailure)
          System.exit(-1)
      }
    }.start()
  }
}

object ClusterBasicTest extends App {
  if (args.isEmpty)
    startup(Seq("2551", "2552", "2553"))
  else
    startup(args)

  def startup(ports: Seq[String]): Unit = {
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
    val clusters = ports.map{ port =>
      val config = ConfigFactory.parseString(configStr)
        .withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port"))
      val system = ActorSystem("cluster", config)
      println(s"############# start node $port ##############")
      val clusterListener = system.actorOf(Props[SimpleClusterListener], name = "listener")
      Thread.sleep(2000)
      println(s"###### clusterListener $port: ${clusterListener}")
      Tuple2(system,clusterListener)
    }
    Thread.sleep(5000)
    println("############# stop cluster ##############")
    clusters.foreach(t => {
      val system = t._1
      val clusterListener = t._2
      clusterListener ! "stop"
      //system.terminate()
    })
  }

}
