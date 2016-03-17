package actor

import akka.actor._
import com.typesafe.config.ConfigFactory

/**
 * Created by cookeem on 15/12/30.
 */

case class RemoteMsg(str: String)
case class LocalMsg(str: String)

class RemoteActor extends Actor {
  val local = context.actorSelection("akka.tcp://system@localhost:2552/user/local*")
  local ! "This msg is from remote! "
  def receive = {
    case RemoteMsg(s) =>
      val str = s"$self receive : RemoteMsg($s)"
      println(str)
      sender() ! RemoteMsg(str)
  }
  override def postStop() = {
    println(s"$self stop!")
  }
}

class LocalActor extends Actor {
  val remote = context.actorOf(Props[RemoteActor], "remote")

  def receive = {
    case LocalMsg(s) =>
      val str = s"$self receive : LocalMsg($s)"
      println(str)
      remote ! RemoteMsg(str)
    case RemoteMsg(s) =>
      println(s"$self receive : RemoteMsg($s)")
    case str: String =>
      println(s"$self receive : $str")
    case "stop" =>
      self ! PoisonPill
  }

  override def postStop = {
    context.children.foreach(ref => context.stop(ref))
    println(s"$self stop!")
  }
}

object RemoteTest extends App {
  val configStr = """
  akka {
    actor {
      #开启远程actor
      provider = "akka.remote.RemoteActorRefProvider"
      deployment {
        #用于远程创建actor的配置
        #注意,此actor不能由system创建
        /remote {
          remote = "akka.tcp://sampleActorSystem@127.0.0.1:2553"
        }
      }
    }
    remote {
      #用于本地远程actor的配置,由system创建的actor配置
      enabled-transports = ["akka.remote.netty.tcp"]
      netty.tcp {
        hostname = "localhost"
        port = 2552
      }
    }
  }"""
  val config = ConfigFactory.parseString(configStr)//.withFallback(ConfigFactory.load())
  val system = ActorSystem("system", config)
  //使用配置创建远程子actor
  val local = system.actorOf(Props[LocalActor], "local")
  val local2 = system.actorOf(Props[LocalActor], "local2")

  local ! LocalMsg("@@@I am Haijian! ")
  local2 ! LocalMsg("###I am Faith! ")
  Thread.sleep(1000)
  local ! "stop"
  local2 ! "stop"
  Thread.sleep(1000)
  system.terminate()
}
