package actor

/**
 * Created by cookeem on 15/12/26.
 */
import akka.actor._

class ExceptionActor extends Actor {
  def receive = {
    case i: Int =>
      val output = (i + 2) / (i - 2)
      println(s"output is $output, $i")
      self ! (i - 1)
    case "kill" =>
      context.stop(self)
  }
  override def postStop() = {
    println(s"${self.path} stop!")
  }
  override def preRestart(e: Throwable, message: Option[Any]) = {
    println(s"${self.path} restart! ${e.getMessage}, ${e.getCause}, $message")
  }
}

object ExceptionActor {
  def props = Props(new ExceptionActor)
}

object ExceptionRestart extends App{
  val system = ActorSystem("system")
  val exceptionActor = system.actorOf(ExceptionActor.props, "exception")
  exceptionActor ! 6

  Thread.sleep(2000)
  exceptionActor ! 10
  system.stop(exceptionActor)
  system.terminate()

}
