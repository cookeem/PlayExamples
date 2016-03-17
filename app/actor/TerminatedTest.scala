package actor

/**
 * Created by cookeem on 15/12/25.
 */
import akka.actor._

class WatchActor(system: ActorSystem) extends Actor {
  val child = context.actorOf(Props.empty, "child")
  context.watch(child) // 只有进行watch,才能获得child的Terminated状态
  var lastSender = system.deadLetters
  def receive = {
    case "kill" =>
      println(s"${child.path} finished")
      context.unwatch(child)
      context.stop(child)
      lastSender = sender()
    case Terminated(`child`) =>
      //捕捉子actor结束事件
      println("child finished")
      lastSender ! "finished"
  }
  override def postStop() = {
    println(s"${self.path} stop!")
  }
}

object WatchActor {
  def props(system: ActorSystem) = Props(new WatchActor(system))
}

object TerminatedTest extends App{
  val system = ActorSystem("system")
  val testActor = system.actorOf(WatchActor.props(system), "test")
  testActor ! "kill"

  system.stop(testActor)
  system.terminate()
}
