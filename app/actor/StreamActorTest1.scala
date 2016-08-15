package actor

import akka.actor.{ActorSystem, ActorRef, Actor, Props}
import akka.stream.actor.ActorPublisher
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Source, Sink}

/**
  * Created by cookeem on 16/7/28.
  */
object StreamActorTest1 extends App {
  //materializer都是在actorSystem中运行
  implicit val system = ActorSystem("QuickStart")
  //stream都是在materializer中运行,必须进行implicit
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  //把source暴露成一个actorRef,用于接收消息
  val source: Source[String, ActorRef] = Source.actorRef[String](Int.MaxValue, OverflowStrategy.fail)
  val ref = Flow[String].to(Sink.foreach(println)).runWith(source.map{str => s"string length is ${str.length}"})
  ref ! "a"
  ref ! "aa"
  ref ! "aaa"

  //把source暴露成一个actorRef, 并且该actorRef可以指定处理逻辑
  val sourceActor: Source[String, ActorRef] = Source.actorPublisher[String](Props(new ActorPublisher[String] {
    def receive = {
      case msg: String =>
        val output = s"$self received: $msg".toUpperCase
        println(output)
    }
  }))
  val refActor = Flow[String].to(Sink.ignore).runWith(sourceActor)
  refActor ! "this is to uppercase"

  //把sink输出到actor
  val actor = system.actorOf(Props(new Actor {
    override def receive = {
      case msg =>
        val output = s"actor received: $msg"
        println(output)
        ref ! output
    }
  }))
  val sink = Sink.actorRef[Int](actor, onCompleteMessage = "stream completed")
  Source(1 to 10).to(sink).run()



}
