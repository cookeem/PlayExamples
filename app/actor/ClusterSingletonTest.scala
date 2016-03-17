package actor

import akka.actor._
import akka.cluster.ClusterEvent._
import akka.cluster._
import akka.cluster.singleton._
import akka.event.LoggingReceive
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration._
import SingletonClusterObj._

/**
 * Created by cookeem on 16/1/6.
 */
object SingletonClusterObj {
  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val timeout = Timeout(5.seconds)
  implicit val executionContext = global

  def managerName = "singleton"
  def managerNodeRole: Option[String] = None // Some("worker") // можно пометитить, на каких нодах может быть запущен синглтон
  def remoteTimeActorName = "remoteTimeActor"

  val configStr = """
    akka {
      loglevel = "INFO"
      actor {
        provider = "akka.cluster.ClusterActorRefProvider"
        debug {
          receive = on
          lifecycle = off
        }
      }
      remote {
        log-remote-lifecycle-events = off
        netty.tcp {
          hostname = "localhost"
          port = 2551
        }
      }
      cluster {
        min-nr-of-members = 2
        auto-down-unreachable-after = 10s
        seed-nodes = [
        "akka.tcp://system@localhost:2551"
        ]
      }
    }
  """
}

class ClusterListener extends Actor with ActorLogging {
  val minClusterSize = 1 // TODO: read from config!
  val cluster = Cluster(context.system)
  var timerCancellable: Option[Cancellable] = None

  case class CheckClusterSize(msg: String)

  def checkClusterSize(msg: String) {
    val clusterSize = cluster.state.members.count { m =>
      m.status == MemberStatus.Up || m.status == MemberStatus.Joining
    }
    log.info(s"###[Listener] event: $msg, cluster size: $clusterSize " +
      s"(${cluster.state.members})")


    if(clusterSize < minClusterSize) {
      log.info(s"###[Listener] cluster size is less than $minClusterSize" +
        ", shutting down!")
      context.system.terminate()
    }
  }

  def scheduleClusterSizeCheck(msg: String) {
    timerCancellable.foreach(_.cancel())
    timerCancellable = Some(
      context.system.scheduler.scheduleOnce(
        1.second, self, CheckClusterSize(msg)
      )
    )
  }

  // subscribe to cluster changes, re-subscribe when restart
  override def preStart() {
    log.info(s"###[Listener] started!")
    cluster.subscribe(self, InitialStateAsEvents, classOf[MemberEvent],
      classOf[UnreachableMember])
  }

  override def postStop() {
    log.info("###[Listener] stopped!")
    cluster.unsubscribe(self)
  }

  def receive = LoggingReceive {
    case msg: MemberEvent =>
      scheduleClusterSizeCheck(msg.toString)

    case msg: UnreachableMember =>
      scheduleClusterSizeCheck(msg.toString)

    case r: CheckClusterSize =>
      checkClusterSize(r.msg)
  }
}

case object GetTimeRequest
case class GetTimeResponse(time: Long)

class RemoteTimeActor extends Actor with ActorLogging {
  override def preStart() = {
    log.info("###[RemoteTimeActor] started!")
  }

  def receive = LoggingReceive {
    case GetTimeRequest =>
      sender ! GetTimeResponse(System.currentTimeMillis())
  }
}

case object SyncTime
case object ScheduleSync

class LocalTimeActor extends Actor with ActorLogging {
  val remoteTimeActor = context.system.actorOf(ClusterSingletonProxy.props(
    singletonManagerPath = s"/user/$managerName/$remoteTimeActorName",
    settings = ClusterSingletonProxySettings(context.system).withRole(managerNodeRole)),
    name = s"${remoteTimeActorName}Proxy")
  val syncPeriod = 5.seconds
  var time = System.currentTimeMillis()

  def scheduleSync() = {
    context.system.scheduler.scheduleOnce(syncPeriod, self, SyncTime)
  }

  override def preStart() = {
    log.info("###[LocalTimeActor] started!")
    scheduleSync()
  }

  // see http://eax.me/akka-scheduler/
  override def postRestart(reason: Throwable) = {}

  def receive = LoggingReceive {
    case SyncTime =>
      val fResp = remoteTimeActor ? GetTimeRequest
      fResp pipeTo self
      fResp onComplete { case _ => self ! ScheduleSync }
    case ScheduleSync =>
      scheduleSync()
    case GetTimeResponse(remoteTime) =>
      log.info(s"###[LocalTimeActor] sync: $remoteTime")
      time = remoteTime
    case GetTimeRequest =>
      sender ! GetTimeResponse(time)
  }
}

object ClusterSingletonTest extends App {
  val config = ConfigFactory.parseString(configStr)
  val system = ActorSystem("system",config)
  system.actorOf(Props[ClusterListener], name = "clusterListener")

  system.actorOf(ClusterSingletonManager.props(
    singletonProps = Props(classOf[RemoteTimeActor]),
    terminationMessage = PoisonPill,
    settings = ClusterSingletonManagerSettings(system).withRole(managerNodeRole)),
    name = managerName)
  //system.actorOf(ClusterSingletonManager.props(
  //  singletonProps = Props[RemoteTimeActor],
  //  singletonName = remoteTimeActorName,
  //  terminationMessage = PoisonPill,
  //  role = managerNodeRole
  //), name = managerName)
  system.actorOf(Props[LocalTimeActor], name = "localTimeActor")
  //Await.ready(system.terminate(),Duration.Inf)
}
