package actor

/**
 * Created by cookeem on 16/1/6.
 * Distributed Publish Subscribe in Cluster
 * How do I send a message to an actor without knowing which node it is running on?
 * How do I send messages to all actors in the cluster that have registered interest in a named topic?
 * 创建distributedPubSubMediator,用于集群的nodes间共享topic内容(基于topic的消息订阅与发布)
 */

import ClusterSubscribeObj._
import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.pubsub._
import com.typesafe.config.ConfigFactory

object ClusterSubscribeObj {
  val configStr = """
    akka {
      loglevel = "WARNING"
      actor.provider = "akka.cluster.ClusterActorRefProvider"
      remote.netty.tcp.port=2551
      remote.netty.tcp.hostname=localhost
      cluster {
        seed-nodes = ["akka.tcp://cluster@localhost:2551"]
        auto-down-unreachable-after = 10s
        metrics.enabled = off
      }
    }"""
}

trait ClusterSubscribeTrait extends Actor {
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

class Subscriber extends ClusterSubscribeTrait {
  import DistributedPubSubMediator.{ Subscribe, SubscribeAck }
  // 为集群创建订阅发布中间件
  val mediator = DistributedPubSub(context.system).mediator
  // 订阅发布中间件订阅topic为"content"的内容
  mediator ! Subscribe("content", self)
  println(s"###create Subscriber mediator: $mediator")
  def receive = eventReceive orElse {
    case s: String =>
      println(s"###$self Got $s")
    // 回送SubscribeAck给对应的ActorRef,表示是否成功订阅topic为"content"的内容
    case SubscribeAck(Subscribe("content", None, `self`)) =>
      println(s"###$self subscribing");
  }
}

class Publisher extends ClusterSubscribeTrait {
  import DistributedPubSubMediator.Publish
  // 为集群创建订阅发布中间件
  val mediator = DistributedPubSub(context.system).mediator
  println(s"@@@create Publisher mediator: $mediator")
  def receive = eventReceive orElse {
    case in: String =>
      val out = in.toUpperCase
      // 向订阅发布中间件发送topic为"content"的内容,此时,集群中所有订阅发布中间件都可以收到topic为"content"的内容
      mediator ! Publish("content", out)
      println(s"@@@$self $mediator Publish(content, $out)")
  }
}

class Destination extends ClusterSubscribeTrait {
  import DistributedPubSubMediator.Put
  // 为集群创建订阅发布中间件
  val mediator = DistributedPubSub(context.system).mediator
  println(s"+++create Destination mediator: $mediator")
  // 向订阅发布中间件发送注册当前路径消息(destination)
  mediator ! Put(self)
  def receive = eventReceive orElse {
    case s: String =>
      println(s"+++Got $s")
  }
}

class Sender extends ClusterSubscribeTrait {
  import DistributedPubSubMediator._
  // 为集群创建订阅发布中间件
  val mediator = DistributedPubSub(context.system).mediator
  println(s"???create Sender mediator: $mediator")
  def receive = eventReceive orElse {
    case in: String =>
      val out = in.toUpperCase
      // 向订阅发布中间件发送指定路径的消息,那么消息就会发送到指定路径
      // 只要知道集群中actor的相对路径,就可以发送消息而不用管actor是否Reachable
      mediator ! Send(path = "/user/destination", msg = out, localAffinity = true)
      println(s"???$self $mediator Send(path = /user/destination*, msg R= $out, localAffinity = true)")
  }
}

object ClusterSubscribeTest extends App {
  val config = ConfigFactory.parseString(configStr)
  val system = ActorSystem("cluster", config)
  Thread.sleep(2000)
  val subscriber1 = system.actorOf(Props[Subscriber], "subscriber1")
  Thread.sleep(2000)
  val subscriber2 = system.actorOf(Props[Subscriber], "subscriber2")
  val subscriber3 = system.actorOf(Props[Subscriber], "subscriber3")
  Thread.sleep(2000)
  val publisher = system.actorOf(Props[Publisher], "publisher")
  Thread.sleep(2000)
  publisher ! "hello"
  Thread.sleep(3000)
  println("##################################")
  val destination = system.actorOf(Props[Destination], "destination")
  val sender = system.actorOf(Props[Sender], "sender")
  Thread.sleep(2000)
  sender ! "hello"
}
