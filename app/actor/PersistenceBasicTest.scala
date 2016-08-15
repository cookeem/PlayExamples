package actor

/**
 * Created by cookeem on 16/1/7.
 */
import akka.actor._
import akka.persistence._
import com.typesafe.config.ConfigFactory

import scala.collection.immutable
import scala.math.abs
import scala.util.Random

import PersistenceBasicObj._

object PersistenceBasicObj {
  case class Cmd(data: String)
  case class Evt(data: String)

  case class ExampleState(events: List[String] = Nil) {
    def updated(evt: Evt): ExampleState = copy(evt.data :: events)
    def size: Int = events.length
    override def toString: String = events.reverse.toString
  }

  //使用mysql存放persistence数据
  val configStr = """
  akka.persistence {
    journal.plugin = "akka-persistence-sql-async.journal"
    snapshot-store.plugin = "akka-persistence-sql-async.snapshot-store"
  }
  akka-persistence-sql-async {
    journal.class = "akka.persistence.journal.sqlasync.MySQLAsyncWriteJournal"
    snapshot-store.class = "akka.persistence.snapshot.sqlasync.MySQLSnapshotStore"
    user = "root"
    password = "asdasd"
    url = "jdbc:mysql://localhost/akka_persistence"
    max-pool-size = 4
    wait-queue-capacity = 10000
    metadata-table-name = "test_metadata"
    journal-table-name = "test_journal"
    snapshot-table-name = "test_snapshot"
  }"""

  //使用levelDB存放persistence数据
  //val configStr = """
  //akka.persistence.journal.plugin = "akka.persistence.journal.leveldb"
  //akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
  //akka.persistence.journal.leveldb.dir = "target/example/journal"
  //akka.persistence.snapshot-store.local.dir = "target/example/snapshots"
  //# DO NOT USE THIS IN PRODUCTION !!!
  //# See also https://github.com/typesafehub/activator/issues/287
  //akka.persistence.journal.leveldb.native = false
  //akka.actor.warn-about-java-serializer-usage = off
  //"""
}

class ExamplePersistentActor extends PersistentActor {
  override def persistenceId = "sample-id-1"
  var state = ExampleState()
  def updateState(event: Evt): Unit = state = state.updated(event)
  def numEvents = state.size
  var meta : SnapshotMetadata = null

  //手动start时重放日志,默认就是开启的
  override def preStart() = {
    //self ! Recovery(toSequenceNr = 20L)
  }

  //手动restart时重放日志,默认就是开启的
  override def preRestart(reason: Throwable, message: Option[Any]) = {
    //self ! Recovery(toSequenceNr = 20L)
  }

  //一个持久化actor通过在启动和重启时重放日志消息实现自动恢复
  val receiveRecover: Receive = {
    //在启动actor的时候,把事件从journal回滚到actor中
    case evt: Evt =>
      updateState(evt)
      println(s"###receiveRecover updateState($evt)")
    //在启动actor的时候,把事件从snapshot回滚到actor中
    case SnapshotOffer(metadata, snapshot: ExampleState) =>
      state = snapshot
      println(s"###receiveRecover SnapshotOffer state = $snapshot, metadata = $metadata")
    case RecoveryCompleted =>
      println(s"###receiveRecover RecoveryCompleted")
  }
  val receiveCommand: Receive = {
    case Cmd(data) =>
      //当接收到命令的时候,把命令中的数据持久化到journal中
      persist(Evt(s"${data}-${numEvents}")) { event =>
        updateState(event)
        context.system.eventStream.publish(event)
      }
      persistAll(immutable.Seq(Evt(s"${data}-${numEvents}"))){event =>
        println(s"###receiveCommand persistAll($event)")
      }
      println(s"###receiveCommand Cmd($data)")
    case "delete" =>
      //从journal中删除所有消息
      deleteMessages(100L)
      println(s"###deleteMessages(100L)")
    case DeleteMessagesSuccess(100L) =>
      println(s"###DeleteMessagesSuccess")
    case DeleteMessagesFailure(e,100L) =>
      println(s"###DeleteMessagesFailure ${e.getMessage} ${e.getCause}")
    case "snap"  =>
      //对jouranl进行snapshot操作,并保存到snapshot中
      saveSnapshot(state)
      println(s"###receiveCommand saveSnapshot($state)")
    case SaveSnapshotSuccess(metadata) =>
      meta = metadata
      println(s"### SaveSnapshotSuccess($metadata)")
    case SaveSnapshotFailure(metadata, reason) =>
      println(s"### SaveSnapshotFailure($metadata)")
    case "deleteSnapshots" =>
      deleteSnapshot(meta.sequenceNr)
      println(s"###deleteSnapshot(${meta.sequenceNr})")
    case DeleteSnapshotSuccess(meta) =>
      println(s"###DeleteSnapshotSuccess($meta)")
    case DeleteSnapshotFailure(meta,e) =>
      println(s"###DeleteSnapshotFailure ${e.getMessage} ${e.getCause}")
    case "print" =>
      println(state)
    case s: String =>
      println(s"###receiveCommand receive string: $s")
  }

  val persistActor2 = context.actorOf(Props[ExamplePersistentActor2], "persistActor2")
  //persistActor2 ! "a"
  //persistActor2 ! "b"


}

//嵌套持久化测试
class ExamplePersistentActor2 extends PersistentActor {
  override def persistenceId = "sample-id-2"
  def receiveRecover: Receive = {
    //在启动actor的时候,假如没有进行snapshot,把事件从journal回滚到actor中
    case s: String => // handle recovery here
      println(s"@@@ExamplePersistentActor2 receiveRecover $s")
  }
  def receiveCommand: Receive = {
    case c: String => {
      sender() ! c
      //嵌套持久化
      persist(s"$c-1-outer") { outer1 =>
        sender() ! outer1
        persist(s"$c-1-inner") { inner1 =>
          sender() ! inner1
        }
      }
      persist(s"$c-2-outer") { outer2 =>
        sender() ! outer2
        persist(s"$c-2-inner") { inner2 =>
          sender() ! inner2
        }
      }
      //推迟持久化,直到之前的持久化已经完成执行
      deferAsync(s"evt-$c-3") { e => sender() ! e }
    }
  }
}

object PersistenceBasicTest extends App {
  val config = ConfigFactory.parseString(configStr)
  val system = ActorSystem("system", config)
  val persistActor = system.actorOf(Props[ExamplePersistentActor], "persistActor")
  val strArr = Array("cookeem","faith","dodo","haijian","wenjing")
  var id = 0
  id = abs(Random.nextInt()) % strArr.length
  persistActor ! Cmd(strArr(id))
  persistActor ! "print"
  persistActor ! "snap"
  id = abs(Random.nextInt()) % strArr.length
  persistActor ! Cmd(strArr(id))
  persistActor ! "print"

  Thread.sleep(2000)
  //persistActor ! "delete"
  //persistActor ! "deleteSnapshots"

  Thread.sleep(4000)
  system.terminate()
}
