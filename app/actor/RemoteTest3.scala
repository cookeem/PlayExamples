package actor

import akka.actor._
import akka.pattern.ask
import akka.remote._
import akka.remote.routing.RemoteRouterConfig
import akka.routing._
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

/**
 * Created by cookeem on 15/12/30.
 * 创建远程router pool
 * 运行方法,打开三个终端,分别运行如下命令
 * sbt 'run-main actor.RemoteTest3 2552'
 * sbt 'run-main actor.RemoteTest3 2553'
 * sbt 'run-main actor.GroupTest3 2552 2553'
 */
class RemoteActor3 extends Actor {
  import context.dispatcher
  implicit val timeout = Timeout(5 seconds)

  //订阅RemotingLifecycleEvent事件,用于接收RemotingLifecycleEvent事件
  context.system.eventStream.subscribe(self, classOf[RemotingLifecycleEvent])

  def receive = {
    case str: String =>
      println(s"$self receive : $str")
    case Terminated(ref) =>
      context.unwatch(ref)
    //订阅RemotingLifecycleEvent事件,接收对应的事件推送
    case e: AssociationErrorEvent =>
      println(s"###AssociationErrorEvent: ${e.getCause} $e")
    case e: RemotingLifecycleEvent =>
      println(s"###RemotingLifecycleEvent: $e")
  }

  //无用
  override def postStop() = {
    println(s"####$self stopping! #####")
    val routees = (self ? akka.routing.GetRoutees).mapTo[Routee]
    println(s"routees: $routees")
    routees.foreach(routee => {
      println(s"$self ! RemoveRoutee($routee)")
      self ! RemoveRoutee(routee)
    })
  }
}

object RemoteTest3 extends App {
  var port = 2552
  if (args.length == 1) {
    try {
      port = args(0).toInt
    } catch {
      case e: Throwable => println(s"Params error: RemoteTest3 <port> ${e.getMessage}, ${e.getCause}")
    }
  }
  val config = ConfigFactory.parseString("""akka.actor.provider="akka.remote.RemoteActorRefProvider"""")
    .withFallback(ConfigFactory.parseString("""akka.remote.netty.tcp.hostname="localhost""""))
    .withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port"))
  val system = ActorSystem("system", config)
  val remote3 = system.actorOf(Props[RemoteActor3], "remote3")
}

object GroupTest3 extends App {
  var ports = Array(2552)
  if (args.length > 0) {
    try {
      ports = args.map(s => s.toInt)
    } catch {
      case e: Throwable => println(s"Params error: GroupTest3 <port1> <port2> ... ${e.getMessage}, ${e.getCause}")
    }
  }
  var addresses = ports.map(i => AddressFromURIString(s"akka.tcp://system@localhost:$i")).toSeq
  val configStr = """
  akka {
    actor {
      #开启远程actor
      provider = "akka.remote.RemoteActorRefProvider"
    }
    remote {
      #用于本地远程actor的配置,由system创建的actor配置
      enabled-transports = ["akka.remote.netty.tcp"]
      netty.tcp {
        hostname = "localhost"
        port = 2550
      }
    }
  }"""
  val config = ConfigFactory.parseString(configStr)
  println(addresses)
  val system = ActorSystem("system", config)
  val group3 = system.actorOf(RemoteRouterConfig(RoundRobinPool(5), addresses).props(Props[RemoteActor3]), "group3")
  group3 ! Broadcast("$$$I am Dodo!")
  Thread.sleep(1000)
  system.stop(group3)
  Thread.sleep(1000)
  system.terminate()
}