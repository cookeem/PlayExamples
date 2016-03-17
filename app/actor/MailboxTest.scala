package actor

import akka.actor._
import akka.dispatch._
import com.typesafe.config.{ConfigFactory, Config}

/**
 * Created by cookeem on 15/12/28.
 */
class MyPrioMailbox(settings: ActorSystem.Settings, config: Config) extends UnboundedPriorityMailbox(
    // Create a new PriorityGenerator, lower prio means more important
    PriorityGenerator {
      // 'highpriority messages should be treated first if possible
      case 'highpriority => 0

      // 'lowpriority messages should be treated last if possible
      case 'lowpriority  => 2

      // PoisonPill when no other left
      case PoisonPill    => 3

      // We default to 1, which is in between high and low
      case otherwise     => 1
    }
)

class MailboxActor extends Actor with ActorLogging {
  self ! 'lowpriority
  self ! 'lowpriority
  self ! 'highpriority
  self ! 'pigdog
  self ! 'pigdog2
  self ! 'pigdog3
  self ! 'highpriority
  self ! PoisonPill

  def receive = {
    case x => log.info(x.toString)
  }
}

object MailboxActor {
  def props = Props[MailboxActor]
}

object MailboxTest extends App {
  val configStr = """prio-mailbox {
    mailbox-type = actor.MyPrioMailbox
    //Other dispatcher configuration goes here
  }"""
  val config = ConfigFactory.parseString(configStr).withFallback(ConfigFactory.load())
  val system: ActorSystem = ActorSystem("system",config)
  val mailActor: ActorRef = system.actorOf(MailboxActor.props.withMailbox("prio-mailbox"), "mailbox")
  system.terminate()
}
