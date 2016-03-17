package controllers

import akka.actor._
import controllers.HelloActor.SayHello
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.duration._
import akka.pattern.ask
import akka.util.Timeout

import javax.inject._

/**
 * Created by cookeem on 16/2/17.
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
  }
}

@Singleton
class ActorApp @Inject() (system: ActorSystem) extends Controller {
  val helloActor = system.actorOf(HelloActor.props, "hello-actor")
  implicit val timeout: Timeout = 5.seconds
  def sayHello(name: String) = Action.async {
    (helloActor ? SayHello(name)).mapTo[String].map { message =>
      Ok(message)
    }
  }
}
