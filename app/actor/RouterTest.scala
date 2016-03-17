package actor

import akka.actor._
import akka.pattern.ask
import akka.routing._
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import RouterObj._
/**
 * Created by cookeem on 15/12/28.
 */

object RouterObj {
  case class Msg(i: Int)
  case class MsgBc(str: String)
}

class Master extends Actor {
  //直接创建router,router中包含多个worker
  var router: Router = {
    val routees: Vector[ActorRefRoutee] = (1 to 5).toVector.map(id => {
      val r = context.actorOf(Worker.props(id), s"worker$id")
      context watch r
      ActorRefRoutee(r)
    })
    Router(RoundRobinRoutingLogic(), routees)
  }


  //路由actor,创建actorRef的router
  val router2: ActorRef = context.actorOf(BalancingPool(5).props(Worker.props(1)), "router2")

  //动态routee数量的router
  val resizer = DefaultResizer(lowerBound = 2, upperBound = 15)
  val router3: ActorRef = context.actorOf(RoundRobinPool(5, Some(resizer)).props(Worker.props(1)), "router3")
  println("router3 routees:")
  implicit val timeout = Timeout(5 seconds)
  val rs = (router3 ? GetRoutees.getInstance).onComplete(t => println(t))

  def receive = {
    case w: String =>
      router.route(w, sender())
    case i: Int =>
      val size = context.children.size
      router2 ! s"size: $size"
    case msg: Msg =>
      router.route(msg, sender())
    case msgBc: MsgBc =>
      //广播给所有的router
      router2 ! Broadcast(s"$msgBc")
      println("router2 ! Broadcast(PoisonPill)")
      router2 ! Broadcast(PoisonPill)
    case Terminated(a) =>
      router = router.removeRoutee(a)
      val r = context.actorOf(Props[Worker])
      context watch r
      router = router.addRoutee(r)
  }
}

class Worker(id: Int) extends Actor {
  def receive = {
    case s: String =>
      println(s"$self Worker receive string: $s, children size: ${context.children.size}")
    case msg: Msg =>
      //sender不是master而是deadletter
      println(s"${sender()} ! (${msg.i},${self})")
      sender() ! s"${msg.i}"
  }
}

object Worker {
  def props(id: Int) = Props(new Worker(id))
}

object RouterTest extends App {
  val configStr = ""
  val config = ConfigFactory.parseString(configStr).withFallback(ConfigFactory.load())
  val system = ActorSystem("system",config)
  val master = system.actorOf(Props[Master],"master")
  master ! "string1"
  master ! "string2"
  master ! "string3"
  master ! "string4"
  master ! "string5"
  master ! "string6"
  master ! "string7"
  master ! "string8"
  master ! "string9"
  master ! "string10"
  Thread.sleep(1000)

  master ! 0
  Thread.sleep(1000)

  master ! Msg(10)
  Thread.sleep(1000)

  master ! MsgBc("hello")
  Thread.sleep(1000)

  system.terminate()
}
