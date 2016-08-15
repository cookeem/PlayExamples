package actor


import akka.actor._
import akka.cluster._
import akka.cluster.ClusterEvent._
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import TransformationObj._

/**
 * Created by cookeem on 16/1/1.
 * Cluster frontends 与 backends 集群
 * 先启动backend,再启动frontend,只要backend没有全部关闭,frontend都能正常发送消息给backend集群
 * sbt "run-main actor.TransformationBackend 2551"
 * sbt "run-main actor.TransformationBackend 2552"
 * sbt "run-main actor.TransformationBackend 2553"
 * sbt "run-main actor.TransformationFrontend"
 * sbt "run-main actor.TransformationFrontend"
 */
case class TransformationJob(text: String)
case class TransformationResult(text: String)
case class JobFailed(reason: String, job: TransformationJob)
case object BackendRegistration
case object FrontendRegistration

object TransformationObj {
  val configStr = """
  akka {
    loglevel = "WARNING"
    log-dead-letters = off
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
          "scala.None$",
          "actor.TransformationJob",
          "actor.TransformationResult",
          "actor.JobFailed",
          "actor.BackendRegistration$",
          "actor.FrontendRegistration$"
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
        "actor.TransformationJob"=kryo
        "actor.TransformationResult"=kryo
        "actor.JobFailed"=kryo
        "actor.BackendRegistration$"=kryo
        "actor.FrontendRegistration$"=kryo
      }
    }
    remote {
      log-remote-lifecycle-events = off
      netty.tcp {
        hostname = "localhost"
        port = 0
      }
    }

    cluster {
      seed-nodes = [
      "akka.tcp://cluster@localhost:2551",
      "akka.tcp://cluster@localhost:2552"]

      auto-down-unreachable-after = 10s
    }
  }"""
}

trait TransformationCluster extends Actor {
  val cluster = Cluster(context.system)
  // subscribe to cluster changes, MemberUp
  // re-subscribe when restart
  override def preStart(): Unit = cluster.subscribe(self, classOf[MemberUp], classOf[UnreachableMember])
  override def postStop(): Unit = cluster.unsubscribe(self)
  var frontends = IndexedSeq.empty[ActorRef]
  var backends = IndexedSeq.empty[ActorRef]
}

class TransformationBackend extends TransformationCluster {
  def receive = {
    case TransformationJob(text) =>
      sender() ! TransformationResult(text.toUpperCase)
      println(s"###$sender receive TransformationJob($text)")
    case state: CurrentClusterState =>
      state.members.filter(_.status == MemberStatus.Up) foreach register
      println(s"###CurrentClusterState: ${state.getMembers}")
    case MemberUp(m) =>
      //当检测到frontend启动的时候,向frontend发送backend已经启动的信息
      register(m)
      println(s"###Member($m) ${m.getRoles} up")
    case FrontendRegistration if !frontends.contains(sender()) =>
      //backend监控frontend,并在frontends中增加对应的frontend
      context watch sender()
      frontends = frontends :+ sender()
      println(s"###$sender Frontend Registration")
      println(s"###frontends: $frontends")
    case UnreachableMember(m) =>
      //当出现成员不可达情况
      cluster.leave(m.address)
      println(s"### ${m.getRoles} ${m.address} Unreachable!")
    case Terminated(a) =>
      //当frontend成员不可达,然后进入到removed状态,death watch发送Terminated信息给watching的backends,并移除frontends
      frontends = frontends.filterNot(_ == a)
      println(s"###$a Frontend Terminated")
  }

  def register(member: Member): Unit = {
    //进行角色检测,集群可以区分角色,frontend启动的情况下,向对应的frontend发送backend注册信息
    if (member.getRoles.contains("frontend")) {
      context.actorSelection(RootActorPath(member.address) / "user" / "frontend") ! BackendRegistration
    }
  }
}

object TransformationBackend extends App {
  // Override the configuration of the port when specified as program argument
  val port = if (args.isEmpty) "0" else args(0)
  val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port")
    .withFallback(ConfigFactory.parseString("akka.cluster.roles = [backend]"))
    .withFallback(ConfigFactory.parseString(configStr))
  val system = ActorSystem("cluster", config)
  system.actorOf(Props[TransformationBackend], name = "backend")
}

class TransformationFrontend extends TransformationCluster {
  var jobCounter = 0

  def receive = {
    case job: TransformationJob =>
      println(s"###backends: $backends")
      if (backends.isEmpty) {
        sender() ! JobFailed("###Service unavailable, try again later", job)
      } else {
        jobCounter += 1
        backends(jobCounter % backends.size) forward job
        println(s"###forward backend: ${backends(jobCounter % backends.size)}")
      }
    case state: CurrentClusterState =>
      state.members.filter(_.status == MemberStatus.Up) foreach register
      println(s"###CurrentClusterState: ${state.getMembers}")
    case MemberUp(m) =>
      register(m)
      println(s"###Member($m) ${m.getRoles} up")
    case BackendRegistration if !backends.contains(sender()) =>
      context watch sender()
      backends = backends :+ sender()
      println(s"###$sender Backend Registration")
      println(s"###backends: $backends")
    case UnreachableMember(m) =>
      cluster.leave(m.address)
      println(s"### ${m.getRoles} ${m.address} Unreachable!")
    case Terminated(a) =>
      backends = backends.filterNot(_ == a)
      println(s"###backends: $backends")
      println(s"###$a Backend Terminated")
  }

  def register(member: Member): Unit = {
    //进行角色检测,集群可以区分角色,backend启动的情况下,向对应的backend发送frontend注册信息
    if (member.getRoles.contains("backend")) {
      context.actorSelection(RootActorPath(member.address) / "user" / "backend") ! FrontendRegistration
    }
  }
}

object TransformationFrontend extends App {
  // Override the configuration of the port when specified as program argument
  val port = if (args.isEmpty) "0" else args(0)
  val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port").
    withFallback(ConfigFactory.parseString("akka.cluster.roles = [frontend]")).
    withFallback(ConfigFactory.parseString(configStr))

  val system = ActorSystem("cluster", config)
  val frontend = system.actorOf(Props[TransformationFrontend], name = "frontend")

  var counter = 0
  import system.dispatcher
  system.scheduler.schedule(5.seconds, 5.seconds) {
    Cluster(system).state.getMembers.foreach { member =>
      println("@@@" + member.address, member.status, member.getRoles)
    }
    counter += 1
    implicit val timeout = Timeout(5 seconds)
    println(s"""###$frontend ? TransformationJob("hello-" + $counter)""")
    (frontend ? TransformationJob("hello-" + counter)) onSuccess {
      case result => println(result)
    }
  }
}
