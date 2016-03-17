package actor

import akka.actor._
import akka.event.Logging

/**
 * Created by cookeem on 15/12/27.
 */

case object Swap

class HotSwapActor extends Actor {
  import context._
  val log = Logging(system, this)

  def receive: Receive = {
    case Swap =>
      log.info("Hi")
      become(recvBecome(), discardOld = false) // push on top instead of replace
  }

  def recvBecome(): Receive = {
    case Swap =>
      log.info("Ho")
      unbecome() // resets the latest 'become' (just for fun)
  }
}

object HotSwapActor {
  def props = Props[HotSwapActor]
}

object BecomeTest extends App {
  val system: ActorSystem = ActorSystem("system")
  val swapActor: ActorRef = system.actorOf(HotSwapActor.props, "swap")
  swapActor ! Swap
  swapActor ! Swap
  swapActor ! Swap
  swapActor ! Swap
  swapActor ! PoisonPill
  system.terminate()

}
