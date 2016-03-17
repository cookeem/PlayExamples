package actor

import java.util

import akka.actor._
import akka.pattern.ask
import akka.routing._
import akka.util.Timeout

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.collection.JavaConversions._
/**
 * Created by cookeem on 15/12/28.
 * 选择现有actorRef到router中,并获取router的routees
 */


class Worker2 extends Actor {
  def receive = {
    case i: Int =>
      println(s"$self receive Int: $i | from $sender")
    case s: String =>
      println(s"$self receive String: $s | from $sender")
  }
}


object RouterTest2 extends App {
  val system = ActorSystem("system")
  val w1 = system.actorOf(Props[Worker2], name = "w1")
  val w2 = system.actorOf(Props[Worker2], name = "w2")
  val w3 = system.actorOf(Props[Worker2], name = "w3")
  val paths = List("/user/w1", "/user/w2", "/user/w3")
  //选择现有worker进入router
  val router: ActorRef = system.actorOf(RoundRobinGroup(paths).props(), "router")

  router ! 1
  router ! 2
  router ! 3
  var i = 0
  implicit val timeout = Timeout(5 seconds)
  val routees: util.List[Routee] = Await.result((router ? GetRoutees.getInstance).mapTo[Routees], timeout.duration).getRoutees
  routees.foreach(r => {
    //以router的身份向对应的routees发送消息
    i += 1
    r.send(s"@@ get routees $i @@",router)
    println(s"routee: ${r}")
  })

  Thread.sleep(1000)
  println("system.stop(worker)")
  system.stop(w1)
  system.stop(w2)
  system.stop(w3)
  Thread.sleep(1000)
  println("system.terminate()")
  system.terminate()

}
