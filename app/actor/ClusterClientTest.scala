package actor

/**
 * Created by cookeem on 16/1/6.
 * 使用ClusterClient向Cluster发送消息
 * sbt "run-main actor.ClusterClientTest service serviceA 2552"
 * sbt "run-main actor.ClusterClientTest service serviceB 2553"
 * sbt "run-main actor.ClusterClientTest service serviceB 2554"
 * sbt "run-main actor.ClusterClientTest client"
 */
import ClusterClientObj._
import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.client.{ClusterClientSettings, ClusterClient, ClusterClientReceptionist}
import com.typesafe.config.ConfigFactory

object ClusterClientObj {
  val configStr = """
    akka {
      loglevel = "WARNING"
      actor {
        provider = "akka.cluster.ClusterActorRefProvider"
        kryo  { #Kryo序列化的配置
          type = "graph"
          idstrategy = "incremental"
          serializer-pool-size = 16
          buffer-size = 4096
          use-manifests = false
          implicit-registration-logging = true
          kryo-trace = false
          classes = [
          "java.lang.String",
          "scala.Some",
          "scala.None$"
          ]
        }
        serializers { #配置可能使用的序列化算法
          java = "akka.serialization.JavaSerializer"
          kryo = "com.romix.akka.serialization.kryo.KryoSerializer"
        }
        serialization-bindings { #配置序列化类与算法的绑定
          "java.lang.String"=kryo
          "scala.Some"=kryo
          "scala.None$"=kryo
        }
      }
      remote.netty.tcp.port=0
      remote.netty.tcp.hostname=localhost
      cluster {
        seed-nodes = ["akka.tcp://cluster@localhost:2552"]
        auto-down-unreachable-after = 10s
        metrics.enabled = off
      }
      extensions = ["akka.cluster.client.ClusterClientReceptionist"]
    } """
}

trait ClusterClientTrait extends Actor {
  var count = 0
  val cluster = Cluster(context.system)
  // subscribe to cluster changes, re-subscribe when restart
  override def preStart(): Unit = {
    //#subscribe
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents, classOf[MemberEvent], classOf[UnreachableMember], classOf[LeaderChanged])
  }
  override def postStop(): Unit = {
    cluster.unsubscribe(self)
    println(s"*** ${context.system} context.system.terminate!!! ")
    context.system.terminate()
  }
  def eventReceive: Receive = {
    case MemberUp(member) =>
      println(s"*** Member is Up: ${self} ${member.address}")
    case UnreachableMember(member) =>
      cluster.down(member.address)
      println(s"*** Member Unreachable: ${self} ${member.address}")
    case MemberRemoved(member, previousStatus) =>
      println(s"*** Member is Removed: ${self} ${member.address} after $previousStatus")
    case MemberExited(member) =>
      println(s"*** Member is Exited: ${self} ${member.address}")
    case LeaderChanged(leader) =>
      println(s"*** Leader is Changed: ${self} ${leader}")
    case evt: MemberEvent => // ignore
      println(s"*** Memver event ${self} ${evt.member.status} ${evt.member.address}")
  }
}

class ServiceClient extends ClusterClientTrait {
  def receive = eventReceive orElse {
    case in: String =>
      val out = in.toUpperCase
      println(s"@@@ $self receive $out")
  }
}

object ClusterClientTest extends App {
  val prompt = "Usage: ClusterTest5 [service <serviceName> <port>] | [client]"
  var serviceName = ""
  var port = "0"
  if (args.isEmpty) {
    println(prompt)
  } else {
    if (args(0) != "service" && args(0) != "client") {
      println(prompt)
    } else {
      var system: ActorSystem = null
      if (args(0) == "service") {
        //启动serviceA和serviceB服务
        serviceName = args(1)
        if (args.length == 3) port = args(2)
        val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port")
          .withFallback(ConfigFactory.parseString(configStr))
        system = ActorSystem("cluster", config)
        val service = system.actorOf(Props[ServiceClient], serviceName)
        ClusterClientReceptionist(system).registerService(service)
      } else if (args(0) == "client") {
        //启动client,对应的system必须与serviceX保持一致
        val config = ConfigFactory.parseString(configStr)
        system = ActorSystem("cluster", config)
        //指定client的初始访问列表
        val initialContacts = Set(
          ActorPath.fromString("akka.tcp://cluster@localhost:2552/system/receptionist"),
          ActorPath.fromString("akka.tcp://cluster@localhost:2553/system/receptionist"))
        val settings: ClusterClientSettings = ClusterClientSettings(system).withInitialContacts(initialContacts)
        val c = system.actorOf(ClusterClient.props(settings), "client")
        //发送给cluster中单个actor
        c ! ClusterClient.Send("/user/serviceA", "hello", localAffinity = true)
        //群发给cluster中多个actor
        c ! ClusterClient.SendToAll("/user/serviceB", "hi")
      }
    }
  }
}
