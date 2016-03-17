package actor

/**
 * Created by cookeem on 16/1/8.
 * 分片集群测试
 * sbt "run-main actor.ClusterShardTest 2551"
 * sbt "run-main actor.ClusterShardTest 2552"
 * sbt "run-main actor.ClusterShardTest 2553"
 * sbt "run-main actor.ClusterShardTest 0"
 * sbt "run-main actor.ClusterShardTest 0"
 */
import ClusterShardObj._
import akka.actor._
import akka.cluster.sharding.{ClusterShardingSettings, ClusterSharding}
import akka.cluster.sharding.ShardRegion._
import akka.persistence.PersistentActor
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

object ClusterShardObj {
  case object Increment
  case object Decrement
  final case class Get(counterId: Long)
  final case class EntityEnvelope(id: Long, payload: Any)
  case object Stop
  final case class CounterChanged(delta: Int)

  val configStr = """
  akka.log-dead-letters = off
  akka.loglevel = "WARNING"
  akka.actor {
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
      "actor.ClusterShardObj$Increment$",  #对象前后必须使用$
      "actor.ClusterShardObj$Decrement$",  #对象前后必须使用$
      "actor.ClusterShardObj$Stop$",  #对象前后必须使用$
      "actor.ClusterShardObj$Get",  #类前必须使用$
      "actor.ClusterShardObj$EntityEnvelope",  #类前必须使用$
      "actor.ClusterShardObj$CounterChanged",  #类前必须使用$
      "java.lang.String",
      "scala.Some",
      "scala.None$",
      "scala.Int"
      ]
    }
    serializers { #配置可能使用的序列化算法
      java = "akka.serialization.JavaSerializer"
      kryo = "com.romix.akka.serialization.kryo.KryoSerializer"
    }
    serialization-bindings { #配置序列化类与算法的绑定
      "actor.ClusterShardObj$Increment$"=kryo  #对象前后都必须使用$
      "actor.ClusterShardObj$Decrement$"=kryo  #对象前后都必须使用$
      "actor.ClusterShardObj$Stop$"=kryo  #对象前后都必须使用$
      "actor.ClusterShardObj$Get"=kryo  #类前必须使用$
      "actor.ClusterShardObj$EntityEnvelope"=kryo  #类前必须使用$
      "actor.ClusterShardObj$CounterChanged"=kryo  #类前必须使用$
      "java.lang.String"=kryo
      "scala.Some"=kryo
      "scala.None$"=kryo
      "java.lang.Integer"=kryo
    }
  }
  akka {
    remote {
      log-remote-lifecycle-events = off
      netty.tcp {
        hostname = "localhost"
        port = 0
      }
    }
    cluster {
      seed-nodes = [
      "akka.tcp://cluster@localhost:2551"]
      auto-down-unreachable-after = 10s
    }
  }
  akka.persistence {
    journal.plugin = "akka-persistence-sql-async.journal"
    snapshot-store.plugin = "akka-persistence-sql-async.snapshot-store"
  }
  akka-persistence-sql-async {
    journal.class = "akka.persistence.journal.sqlasync.MySQLAsyncWriteJournal"
    snapshot-store.class = "akka.persistence.snapshot.sqlasync.MySQLSnapshotStore"
    user = "root"
    password = "man8080"
    url = "jdbc:mysql://localhost/akka_persistence"
    max-pool-size = 4
    wait-queue-capacity = 10000
    metadata-table-name = "test_metadata"
    journal-table-name = "test_journal"
    snapshot-table-name = "test_snapshot"
  }"""
}

class PersistentCounter extends PersistentActor {
  context.setReceiveTimeout(15.seconds)
  // self.path.name is the entity identifier (utf-8 URL-encoded)
  override def persistenceId: String = "Counter-" + self.path.name
  println(s"persistenceId: $persistenceId")
  var count = 0
  def updateState(event: CounterChanged): Unit = count += event.delta

  override def receiveRecover: Receive = {
    case evt: CounterChanged =>
      updateState(evt)
      println(s"@@@receiveRecover receive: updateState($evt)")
  }

  override def receiveCommand: Receive = {
    case Increment      =>
      persist(CounterChanged(+1))(updateState)
      println(s"###receiveCommand $self receive: Increment ${Increment.getClass}| $count")
    case Decrement      =>
      persist(CounterChanged(-1))(updateState)
      println(s"###receiveCommand $self receive: Decrement ${Decrement.getClass} | $count")
    case Get(_)         =>
      sender() ! count
      println(s"###receiveCommand ${self} receive: Get | ${sender} ! ${count}")
    case ReceiveTimeout =>
      context.parent ! Passivate(stopMessage = Stop)
      println(s"###receiveCommand $self receive: ReceiveTimeout | ${context.parent} ! Passivate(stopMessage = Stop)")
    case Stop           =>
      context.stop(self)
      println(s"###receiveCommand $self receive: Stop ${Stop.getClass}")
  }
}

class ShardSenderActor(ref: ActorRef) extends Actor {
  var count = 0
  def receive = {
    case "start" =>
      count += 1
      ref ! Increment
      Thread.sleep(500)
      ref ! Increment
      Thread.sleep(500)
      ref ! Decrement
      Thread.sleep(500)
      ref ! Get(count)
      Thread.sleep(500)
      ref ! EntityEnvelope(count, Increment)
  }
}

object ClusterShardTest extends App {
  var port = "0"
  if (args.length == 1) port = args(0)
  val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port")
    .withFallback(ConfigFactory.parseString(configStr))
  println(s"config: $config")
  val system = ActorSystem("cluster", config)
  println(s"@@@ system: $system")
  val extractEntityId: ExtractEntityId = {
    case EntityEnvelope(id, payload) =>
      println(s"***EntityEnvelope : EntityEnvelope($id, $payload)")
      (id.toString, payload)
    case msg @ Get(id)               =>
      println(s"***msg @ Get(id) : $msg @ Get($id)")
      (id.toString, msg)
  }
  val numberOfShards = 3
  val extractShardId: ExtractShardId = {
    case EntityEnvelope(id, x) =>
      println(s"***EntityEnvelope(id, x): EntityEnvelope($id, $x)")
      (id % numberOfShards).toString
    case Get(id) =>
      println(s"***Get(id): Get($id)")
      (id % numberOfShards).toString
  }
  println(s"@@@ extractEntityId: $extractEntityId, extractShardId: $extractShardId")
  val counterRegionService = ClusterSharding(system).start(
    typeName = "Counter",
    entityProps = Props[PersistentCounter],
    settings = ClusterShardingSettings(system),
    extractEntityId = extractEntityId,
    extractShardId = extractShardId
  )
  Thread.sleep(2000)
  println(s"@@@ counterRegionService: $counterRegionService")
  val counterRegionClient = ClusterSharding(system).shardRegion("Counter")
  println(s"@@@ counterRegionClient: $counterRegionClient")
  import system.dispatcher
  if (port == "0") {
    val shardSenderActor = system.actorOf(Props(classOf[ShardSenderActor], counterRegionClient),"shardSenderActor")
    println(s"@@@ shardSenderActor: $shardSenderActor")
    val cancellable = system.scheduler.schedule(0.seconds, 8.seconds, shardSenderActor, "start")
  }
}
