package actor

import akka.actor._
import akka.stream._
import akka.stream.scaladsl._

import scala.concurrent.Future

/**
 * Created by cookeem on 16/3/11.
 */
object StreamTest3 extends App {
  //materializer都是在actorSystem中运行
  implicit val system = ActorSystem("QuickStart")
  //stream都是在materializer中运行,必须进行implicit
  implicit val materializer = ActorMaterializer()
  //必须import一个executionContext
  import materializer.executionContext

  //简单版
  val source = Source[Int](1 to 10)
  //必须指定foreach的Int类型,对接source
  val sink = Sink.foreach[Int](i => println(s"receive $i"))
  val graph = source.to(sink)
  graph.run()

  //与actor集成
  def run(actor: ActorRef): Future[Unit] = {
    Future { Thread.sleep(300); actor ! 1 }
    Future { Thread.sleep(200); actor ! 2 }
    Future { Thread.sleep(100); actor ! 3 }
  }
  val s = Source.actorRef[Int](bufferSize = 0, OverflowStrategy.fail).mapMaterializedValue(run)
  s.runForeach(println)

  //与actor集成,指定sink的actor
  val actor = system.actorOf(Props(new Actor {
    override def receive = {
      case msg => println(s"actor received: $msg")
    }
  }))
  val sink2 = Sink.actorRef[Int](actor, onCompleteMessage = "stream completed")
  val runnable = Source(1 to 10).to(sink2)
  runnable.run()

  //flow简单样例
  val flow1 = Flow[Int].map(elem => elem * -1)
  val flow2 = Flow[Int].map(elem => elem * 2)
  source.via(flow1).via(flow2).to(sink).run()
  val flow3 = Flow.fromSinkAndSource(sink,source)
  flow3.runWith(source,sink)

  val src1 = Source(List(1,1,3,4,5,3))
  val src2 = Source(6 to 10)
  val zipSrc = src1.zip(src2)
  val groupFlow = zipSrc.groupBy(6, _._1)
  groupFlow.to(Sink.foreach(println)).run()
}
