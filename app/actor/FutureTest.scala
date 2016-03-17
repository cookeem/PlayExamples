package actor

import akka.actor._
import akka.pattern.ask
import akka.routing._
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

/**
 * Created by cookeem on 15/12/30.
 */
class SumActor extends Actor {
  var msgSum = 0
  def receive = {
    case i: Int =>
      println(s"$self $i Thread.sleep(1000)")
      Thread.sleep(1000)
      msgSum = msgSum + i
      println(s"$self return $msgSum")
      sender ! msgSum
  }
}

class CountActor extends Actor {
  import context.dispatcher
  val sumActor = context.actorOf(RoundRobinPool(2).props(Props[SumActor]), "sum")
  implicit val timeout = Timeout(3 seconds)
  def receive = {
    case i: Int =>
      val listFuture: List[Future[Int]] = (1 to i).map(j => (sumActor ? j).mapTo[Int]).toList
      println(s"(1 to i).map(j => (sumActor ? j).mapTo[Int]).toList = $listFuture")
      val futureList: Future[List[String]] = Future.sequence(listFuture).map(list => list.map(k => s"sum is $k"))
      futureList.onComplete{
        case Success(i) => println(s"sum reply: $i")
        case Failure(e) => println(s"error: ${e.getMessage}, ${e.getCause}")
      }
  }
}

object FutureTest extends App {
  val system = ActorSystem("system")
  val countActor = system.actorOf(Props[CountActor], "count")
  countActor ! 5

  Thread.sleep(5000)
  system.terminate()


}
