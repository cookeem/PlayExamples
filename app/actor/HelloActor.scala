package actor

import akka.actor._

/**
 * Created by cookeem on 15/12/12.
 */
object HelloActor {
  def props = Props[HelloActor]

  case class SayHello(name: String)
}

class HelloActor extends Actor {
  import HelloActor._

  def receive = {
    case SayHello(name: String) =>
      sender() ! "Hello, " + name
      println("Hello, " + name)
  }
}