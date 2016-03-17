package actor

import akka.actor._
import akka.routing._
import com.typesafe.config.ConfigFactory

/**
 * Created by cookeem on 15/12/30.
 */

case class RecvMsg(str: String)

class RemoteActor2 extends Actor {
  def receive = {
    case str: String =>
      println(s"$self receive : $str")
    case RecvMsg(str) =>
      println(s"$self receive : RecvMsg($str)")
      sender() ! RecvMsg(s"remote2 receive $str")
  }
}

class LocalActor2 extends Actor {
  val remote2 = context.actorSelection("akka.tcp://system@localhost:2554/user/remote2")
  val identifyId = 1
  //用于标识actorSelection对应的identifyId
  remote2 ! Identify(identifyId)

  remote2 ! RecvMsg("I am cookeem")
  def receive = {
    //通过actorSelection获取的selection需要通过这种方式获取ref
    case ActorIdentity(`identifyId`, Some(ref)) =>
      println(s"$context.watch($ref) identifyId: $identifyId")
      context.watch(ref)
    case str: String =>
      println(s"$self receive : $str")
  }
}

object RemoteTest2 extends App {
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
        port = 2554
      }
    }
  }"""
  val config = ConfigFactory.parseString(configStr)//.withFallback(ConfigFactory.load())
  val system = ActorSystem("system", config)
  val remote2 = system.actorOf(BalancingPool(5).props(Props[RemoteActor2]), "remote2")
  remote2 ! Broadcast("$$$I am Dodo!")
  println("###############")
  //Thread.sleep(1000)
  //system.terminate()
}

object LocalTest2 extends App {
  var port = 2552
  if (args.length == 1) {
    try {
      port = args(0).toInt
    } catch {
      case e: Throwable => println(s"Params error: LocalTest2 <port> ${e.getMessage}, ${e.getCause}")
    }
  }
  val configStr = """
  akka {
    actor {
      #开启远程actor
      provider = "akka.remote.RemoteActorRefProvider"
    }
  }"""
  val config = ConfigFactory.parseString(configStr)
    .withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port"))
  val system = ActorSystem("system", config)
  val local2 = system.actorOf(Props[LocalActor2], "local2")
}
