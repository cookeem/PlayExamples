package actor

import akka.actor._
import akka.pattern.{ask, gracefulStop}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._

/**
 * Created by cookeem on 15/12/24.
 */
case class SayHi(name: String, count: Int = 0)
case class SayHo(name: String, count: Int = 0)
case class AskHo(i: Int)

class HiActor extends Actor with ActorLogging {
  val hxActor = context.actorOf(HxActor.props, "hx-actor")
  context.watch(hxActor)
  context.setReceiveTimeout(50 milliseconds)
  def receive = {
    case SayHi(name: String, count: Int) =>
      context.actorSelection("akka://hiho-system/user/ho-actor") ! SayHo(name, count + 1)
      println(s"Hi, $name, $count, ${context.self.path}")
      if (count > 5) {
        sender() ! "stop"
      }
      context.children.foreach(ar =>
        ar ! s"${ar.path} $name $count"
      )
    case "stop" =>
      context.children.foreach(ar => {
        context.unwatch(ar)
        context.stop(ar)
        println(s"${ar.path} stop!")
      })
      context.stop(self)
      println("Hi stop")
    case _ => println("Receive type error!")
  }
}

object HiActor {
  def props = Props[HiActor]
}

class HxActor extends Actor with ActorLogging {
  override def postStop = {
    println(s"Hx stop, ${self.path}")
  }

  def receive = {
    case str: String =>
      println(s"Hx get string: $str")
  }
}

object HxActor {
  def props = Props[HxActor]
}

class HoActor extends Actor with ActorLogging {
  def receive = {
    case SayHo(name: String, count: Int) =>
      context.actorSelection("akka://hiho-system/user/hi-actor") ! SayHi(name, count + 1)
      println(s"Ho, $name, $count, ${context.self.path}")
      if (count > 5) {
        sender() ! "stop"
      }
    case "stop" =>
      context.stop(self)
      println("Ho stop")
    case AskHo(i: Int) =>
      sender() ! (i * i)
    case _ =>
      log.error("Receive type error!")
  }
}

object HoActor {
  def props = Props[HoActor]
}

object ActorTest extends App {
  val configStr = """my-dispatcher {
    # Dispatcher 是基于事件的派发器的名称
    type = Dispatcher
    # 使用何种ExecutionService
    executor = "fork-join-executor"
    # 配置 fork join 池
      fork-join-executor {
        # 容纳基于因子的并行数量的线程数下限
        parallelism-min = 2
        # 并行数（线程）... ceil(可用CPU数＊因子）
        parallelism-factor = 2.0
        # 容纳基于因子的并行数量的线程数上限
        parallelism-max = 10
      }
    # Throughput 定义了线程切换到下一个actor之前处理的消息数上限
    # 设置成1表示尽可能公平。
    throughput = 100
  }"""
  val config = ConfigFactory.parseString(configStr).withFallback(ConfigFactory.load())
  println(s"config: ${config.root().render()}")
  val system: ActorSystem = ActorSystem("hiho-system",config)
  val hiActor: ActorRef = system.actorOf(HiActor.props.withDispatcher("my-dispatcher"), "hi-actor")
  val hoActor: ActorRef = system.actorOf(HoActor.props.withDispatcher("my-dispatcher"), "ho-actor")
  val name = "cookeem"
  hiActor ! SayHi(name,0)
  implicit val timeout = Timeout(5 seconds)
  implicit val dispatcher = system.dispatcher
  val futureInt = (hoActor ? AskHo(5))(5 seconds)
  println("futureInt: ",Await.result(futureInt, 5 seconds))

  //优雅的终止actor
  try {
    println(s"begin to gracefulStop")
    val stopped: Future[Boolean] = gracefulStop(hiActor, 5 seconds, "stop")
    Await.result(stopped, 6 seconds)
    println(s"gracefulStop($hiActor, 5 seconds, stop)")
    // the actor has been stopped
  } catch {
    // the actor wasn't stopped within 5 seconds
    case e: akka.pattern.AskTimeoutException =>
  }

  system.terminate()
}