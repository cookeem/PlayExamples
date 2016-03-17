package actor

import akka.actor._
/**
 * Created by cookeem on 15/12/27.
 */
class IdentifyActor(identifyId: Int) extends Actor {
  context.actorSelection("/user/actor"+identifyId) ! Identify(identifyId)

  def receive = {
    case ActorIdentity(`identifyId`, Some(ref)) =>
      context.watch(ref)
      println(s"$identifyId context.watch($ref)")
      context.watch(self)
      println(s"$identifyId context.watch($self)")
      context.become(active(ref))
      println(s"$identifyId context.become(active($ref))")
    case ActorIdentity(`identifyId`, None) =>
      context.stop(self)
      println(s"$identifyId receive ActorIdentity(`identifyId`, None)")

  }

  def active(another: ActorRef): Actor.Receive = {
    case Terminated(`another`) =>
      context.stop(self)
      println(s"context.stop($self)")
  }
}

object IdentifyActor {
  def props(identifyId: Int) = Props(new IdentifyActor(identifyId))
}

object IdentifyTest extends App{
  val system = ActorSystem("system")
  val actor1 = system.actorOf(IdentifyActor.props(3), "actor1")
  val actor2 = system.actorOf(IdentifyActor.props(1), "actor2")
}
