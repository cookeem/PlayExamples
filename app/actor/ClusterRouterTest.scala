package actor

/**
 * Created by cookeem on 16/1/4.
 * cluster awared router
 * worker为jobrouter启动的routee
 * 使用kryo进行java.lang.String的序列化
 * sbt "run-main actor.ClusterRouterTest router 2551"
 * sbt "run-main actor.ClusterRouterTest router 2552"
 * sbt "run-main actor.ClusterRouterTest router 2553"
 * sbt "run-main actor.ClusterRouterTest service 2554"
 * sbt "run-main actor.ClusterRouterTest service 2555"
 */
import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.routing._
import akka.routing._
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._

import ClusterRouterObj._

object ClusterRouterObj {
  val configStr = """
      akka {
        loglevel = "WARNING"
        log-dead-letters = on
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
        remote {
          log-remote-lifecycle-events = off
          netty.tcp {
            hostname = "localhost"
          }
        }
        cluster {
          seed-nodes = ["akka.tcp://cluster@localhost:2551"]
          auto-down-unreachable-after = 10s
          metrics.enabled = off
        }
        extensions = ["akka.cluster.metrics.ClusterMetricsExtension"]
      }"""
}

class JobWorker extends Actor {
  println(s"### Worker start: $self")
  def receive = {
    case s: String =>
      println(s"### $self receive :$s")
    case ActorIdentity(1,Some(ref)) =>
      sender() ! ref
    case e =>
      println(s"### Unhandled message ${self} ${e.getClass} $e ")
  }
}

trait ClusterRouterTrait extends Actor {
  var count = 0
  val cluster = Cluster(context.system)
  // subscribe to cluster changes, re-subscribe when restart
  override def preStart(): Unit = {
    //#subscribe
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents, classOf[MemberEvent], classOf[UnreachableMember], classOf[LeaderChanged])
  }
  override def postStop(): Unit = {
    cluster.unsubscribe(self)
    println(s"@@@ ${context.system} context.system.terminate!!! ")
    context.system.terminate()
  }
  def eventReceive: Receive = {
    case MemberUp(member) =>
      println(s"@@@ Member is Up: ${self} ${member.address}")
    case UnreachableMember(member) =>
      cluster.down(member.address)
      println(s"@@@ Member Unreachable: ${self} ${member.address}")
    case MemberRemoved(member, previousStatus) =>
      println(s"@@@ Member is Removed: ${self} ${member.address} after $previousStatus")
    case MemberExited(member) =>
      println(s"@@@ Member is Exited: ${self} ${member.address}")
    case LeaderChanged(leader) =>
      println(s"@@@ Leader is Changed: ${self} ${leader}")
    case evt: MemberEvent => // ignore
      //println(s"@@@ Memver event ${self} ${evt.member.status} ${evt.member.address}")
  }
}

class JobRouter extends ClusterRouterTrait with ActorLogging {
  // 通过JobRouter以RoundRobinPool方式启动routees
  val router = context.actorOf(
    ClusterRouterPool(RoundRobinPool(3), //用Pool来创建 轮询路由
      ClusterRouterPoolSettings(
        totalInstances = 50, //集群中最多多少个
        maxInstancesPerNode = 3, //每个节点最多多少个,此配置影响创建多少个worker
        allowLocalRoutees = false, //不在本地节点创建, 只在worker节点上创建
        useRole = None
      )
    ).props(Props[JobWorker]),
    name = "worker-router")

  def receive  = {
    eventReceive.orElse {
      case "stop" =>
        self ! PoisonPill
      case s: String =>
        count += 1
        //把接收的消息发送给由Worker构成的routees: /user/jobrouter/worker-router
        router ! s"@@ $s | $count @@"
      case e =>
        println(s"@@@ Unhandled message ${self} ${e.getClass} $e ")
    }
  }
}

class JobService extends ClusterRouterTrait with ActorLogging {
  val routeesPaths = List("/user/jobrouter")
  //通过routerGroup调用JobRouter创建的routees
  val serviceRouter = context.actorOf(
    ClusterRouterGroup(
      RandomGroup(routeesPaths),
      ClusterRouterGroupSettings(
        totalInstances = 100,
        routeesPaths = routeesPaths,
        allowLocalRoutees = false,
        useRole = None
      )
    ).props(),
    name = "service-router")

  def receive = {
    eventReceive.orElse {
      case "stop" =>
        self ! PoisonPill
      case s: String =>
        count += 1
        //把接收的消息发送给由JobRouter构成的routees: /user/jobrouter
        serviceRouter ! s"** $s | $count **"
      case e =>
        println(s"*** Unhandled message ${self} ${e.getClass} $e ")
    }
  }
}

object ClusterRouterTest extends App {
  println("Usage ClusterTest3 [router|service] <port>")
  var clusterType = "router"
  var port = "2551"
  if (args.length == 2) {
    if (args(0) == "router" || args(0) == "service") clusterType = args(0)
    port = args(1)
  }
  startup(clusterType,port)

  def startup(clusterType: String, port: String): Unit = {
    println("############# start cluster ##############")
    val config = ConfigFactory.parseString(configStr)
      .withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port"))
    val system = ActorSystem("cluster", config)
    println(s"############# start node $port ##############")
    var ref: ActorRef = null
    if (clusterType == "router") {
      ref = system.actorOf(Props[JobRouter], name = "jobrouter")
    } else {
      ref = system.actorOf(Props[JobService], name = "jobservice")
    }
    Thread.sleep(1000)

    println(s"############# finish start nodes ##############")
    Thread.sleep(3000)

    //把各条消息平均分配到/user/jobrouter,然后jobrouter再把各条消息平均分配到/user/jobrouter/worker-router
    var counter = 0
    import system.dispatcher
    if (clusterType == "service") {
      system.scheduler.schedule(5.seconds, 5.seconds) {
        counter += 1
        val msg = s"Now the counter is: $counter"
        println(msg)
        ref ! msg
      }
    }
  }
}
