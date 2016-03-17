package actor

import akka.actor._

/**
 * Created by cookeem on 16/1/5.
 */

class RecvActor extends Actor {
  def becomeReceive: Receive = {
    case "start" =>
      println(s"@@@BecomeActor start")
  }
  def receive = {
    becomeReceive.orElse {
      case "stop" =>
        println(s"###BecomeActor stop")
      case e =>
        println(s"###BecomeActor receive unhandled message @$e@ ${e.getClass}")
    }
  }
}

object RecvComposeTest extends App {
  val system = ActorSystem("system")
  val becomeActor = system.actorOf(Props[RecvActor], "becomeActor")
  becomeActor ! "start"
  becomeActor ! "stop"
  becomeActor ! "start"
  becomeActor ! "unhandle"
}
