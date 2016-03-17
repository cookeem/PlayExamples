package actor

import akka.actor._

/**
 * Created by cookeem on 15/12/29.
 */
object GumballMachineProtocol {
  case object InsertQuarter
  case object EjectQuarter
  case object TurnCrank
  case object GumballCount
}

object GumballMachine {
  sealed trait State
  case object NoQuarterState extends State
  case object HasQuarterState extends State
  case object SoldState extends State
  case object SoldOutState extends State
}

class GumballMachine extends Actor with FSM[GumballMachine.State, Int] {
  import GumballMachineProtocol._
  import GumballMachine._

  startWith(NoQuarterState, 5)

  when(NoQuarterState) {
    case Event(InsertQuarter, gumballCount) if gumballCount > 0 =>
      println(s"case Event(InsertQuarter, gumballCount) if $gumballCount > 0 => goto(HasQuarterState)")
      goto(HasQuarterState)
  }

  when(HasQuarterState) {
    case Event(EjectQuarter,_) =>
      println(s"case Event(EjectQuarter,_) => goto(NoQuarterState)")
      goto(NoQuarterState)
    case Event(TurnCrank, _) =>
      println(s"case Event(TurnCrank, _) => goto(SoldState)")
      goto(SoldState)
  }

  when(SoldState) {
    case _ =>
      println(s"case _ => stay")
      stay
  }

  whenUnhandled {
    case Event(GumballCount, gumballCount) =>
      println(s"case Event(GumballCount, $gumballCount) => sender() ! $gumballCount; stay()")
      sender() ! gumballCount; stay()
    case x =>
      println(s"case $x => stay()")
      stay()
  }

  initialize()
}

object FsmTest extends App {
  import GumballMachineProtocol._
  val system = ActorSystem("fsm-system")
  val gumball: ActorRef = system.actorOf(Props[GumballMachine],"gumball")
  gumball ! InsertQuarter

}
