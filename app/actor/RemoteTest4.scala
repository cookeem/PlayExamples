package actor

import akka.actor._
import com.typesafe.config.ConfigFactory

/**
 * Created by cookeem on 16/1/3.
 * 通过localSystem:2552创建远程remoteSystem:2553/remoteActor的演示
 * 必须先创建remoteSystem:2553才能向他创建remoteActor
 */
class RemoteActor4 extends Actor {
  //创建的remoteActor路径比较奇怪,包含来源的地址,如下:
  //akka://remoteSystem/remote/akka.tcp/localSystem@localhost:2552/user/remoteActor#684185364
  println(s"###create remoteActor: $self")
  def receive = {
    case s: String =>
      println(s"### $self receive $s")
  }
}

object RemoteTest4 extends App {
  val configLocal = ConfigFactory.parseString(""" akka.actor.provider = "akka.remote.RemoteActorRefProvider" """)
    .withFallback(ConfigFactory.parseString(""" akka.remote.enabled-transports = ["akka.remote.netty.tcp"] """))
    .withFallback(ConfigFactory.parseString(""" akka.remote.netty.tcp.hostname = "localhost" """))
    .withFallback(ConfigFactory.parseString(""" akka.remote.netty.tcp.port = 2552 """))
    .withFallback(ConfigFactory.parseString(""" akka.actor.deployment = { /remoteActor { remote = "akka.tcp://remoteSystem@localhost:2553" }} """))

  val configRemote = ConfigFactory.parseString(""" akka.actor.provider = "akka.remote.RemoteActorRefProvider" """)
    .withFallback(ConfigFactory.parseString(""" akka.remote.enabled-transports = ["akka.remote.netty.tcp"] """))
    .withFallback(ConfigFactory.parseString(""" akka.remote.netty.tcp.hostname = "localhost" """))
    .withFallback(ConfigFactory.parseString(""" akka.remote.netty.tcp.port = 2553 """))

  val localSystem = ActorSystem("localSystem", configLocal)
  Thread.sleep(2000)
  val remoteSystem = ActorSystem("remoteSystem", configRemote)
  Thread.sleep(2000)
  val remoteActor = localSystem.actorOf(Props[RemoteActor4], "remoteActor")
  Thread.sleep(2000)
  remoteActor ! "I am Haijian!"
  Thread.sleep(2000)
  remoteSystem.terminate()
  localSystem.terminate()
}
