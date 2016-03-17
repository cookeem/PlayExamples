package actor

/**
 * Created by cookeem on 16/1/8.
 * 持久化,保证至少发送一次
 */
import PersistenceOnceObj._
import akka.actor._
import akka.persistence._
import com.typesafe.config.ConfigFactory

import scala.math._
import scala.util.Random

object PersistenceOnceObj {
  case class Msg(deliveryId: Long, s: String)
  case class Confirm(deliveryId: Long)

  sealed trait Evt
  case class MsgSent(s: String) extends Evt
  case class MsgConfirmed(deliveryId: Long) extends Evt

  //使用levelDB存放persistence数据
  //使用kryo进行序列化
  val configStr = """
  akka.actor {
    kryo  { #Kryo序列化的配置
        type = "graph"
        idstrategy = "incremental"
        serializer-pool-size = 16
        buffer-size = 4096
        use-manifests = false
        implicit-registration-logging = true
        kryo-trace = false
        classes = [
        "actor.PersistenceOnceObj$Msg",  #类前必须使用$
        "actor.PersistenceOnceObj$Confirm",  #类前必须使用$
        "actor.PersistenceOnceObj$Evt",  #类前必须使用$
        "actor.PersistenceOnceObj$MsgSent",  #类前必须使用$
        "actor.PersistenceOnceObj$MsgConfirmed",  #类前必须使用$
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
      "actor.PersistenceOnceObj$Msg"=kryo  #类前必须使用$
      "actor.PersistenceOnceObj$Confirm"=kryo  #类前必须使用$
      "actor.PersistenceOnceObj$Evt"=kryo  #类前必须使用$
      "actor.PersistenceOnceObj$MsgSent"=kryo  #类前必须使用$
      "actor.PersistenceOnceObj$MsgConfirmed"=kryo  #类前必须使用$
      "java.lang.String"=kryo
      "scala.Some"=kryo
      "scala.None$"=kryo
    }
  }
  akka.persistence {
    journal.plugin = "akka.persistence.journal.leveldb"
    snapshot-store.plugin = "akka.persistence.snapshot-store.local"
    journal.leveldb.dir = "target/example/journal"
    snapshot-store.local.dir = "target/example/snapshots"
    journal.leveldb.native = false
  }"""

}

class PersistentOnceActor(destination: ActorSelection) extends PersistentActor with AtLeastOnceDelivery {
  override def persistenceId: String = "persistence-once-id"
  override def receiveRecover: Receive = {
    case evt: Evt =>
      println(s"receiveRecover receive $evt | updateState($evt)")
      updateState(evt)
    case RecoveryCompleted =>
      println(s"receiveRecover receive RecoveryCompleted!")
  }
  override def receiveCommand: Receive = {
    case s: String =>
      persist(MsgSent(s))(updateState)
    case Confirm(deliveryId) =>
      persist(MsgConfirmed(deliveryId))(updateState)
  }
  def updateState(evt: Evt): Unit = evt match {
    case MsgSent(s) =>
      deliver(destination)(deliveryId => Msg(deliveryId, s))
      println(s"$self receive MsgSent($s) | deliver($destination)(deliveryId => Msg(deliveryId, $s))")
    case MsgConfirmed(deliveryId) =>
      println(s"$self receive MsgConfirmed($deliveryId) | confirmDelivery($deliveryId)")
      confirmDelivery(deliveryId)
  }
}

class PersistentOnceDestinationActor extends Actor {
  def receive = {
    case Msg(deliveryId, s) =>
      println(s"$self receive Msg($deliveryId, $s) | $sender ! Confirm($deliveryId)")
      sender() ! Confirm(deliveryId)
  }
}

object PersistenceOnceTest extends App {
  val config = ConfigFactory.parseString(configStr)
  val system = ActorSystem("system", config)
  val persistOnceDestActor = system.actorOf(Props[PersistentOnceDestinationActor], "persistOnceDestActor")
  println(s"persistOnceDestActor: $persistOnceDestActor")
  val destination = system.actorSelection("/user/persistOnceDestActor")
  println(s"destination: $destination")
  val persistOnceActor = system.actorOf(Props(classOf[PersistentOnceActor],destination), "persistOnceActor")
  println(s"persistOnceActor: $persistOnceActor")

  val strArr = Array("cookeem","faith","dodo","haijian","wenjing")
  var id = 0
  id = abs(Random.nextInt()) % strArr.length
  persistOnceActor ! strArr(id)
  id = abs(Random.nextInt()) % strArr.length
  persistOnceActor ! strArr(id)
  id = abs(Random.nextInt()) % strArr.length
  persistOnceActor ! strArr(id)
  id = abs(Random.nextInt()) % strArr.length
  persistOnceActor ! strArr(id)
  Thread.sleep(2000)
  system.stop(persistOnceActor)
  system.stop(persistOnceDestActor)
  Thread.sleep(2000)
  system.terminate()
}
