package actor

import java.nio.file.Paths

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._
import akka.util.ByteString

import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * Created by cookeem on 16/3/10.
 */
object StreamTest1 extends App {
  //materializer都是在actorSystem中运行
  implicit val system = ActorSystem("QuickStart")
  //stream都是在materializer中运行,必须进行implicit
  implicit val materializer = ActorMaterializer()
  //创建一个Source
  val source: Source[Int, NotUsed] = Source(1 to 100)

  val actorSource = Source.actorRef[String](bufferSize = Int.MaxValue, OverflowStrategy.fail)
  actorSource.mapMaterializedValue { ref =>
    println(ref)
    ref ! "hello"
  }
  //对Source进行阶乘转换transformation
  val factorials = source.scan(BigInt(1)){(acc, next) => acc * next}
  //把Source输出到文件,FileIO是一种Sink,从source到sink,runWith的参数是sink
  val result: Future[IOResult] = factorials.map(num => ByteString(s"$num\n"))
    .runWith(FileIO.toPath(Paths.get("factorials.txt")))

  //除了source可以重用,sink也可以重用,把sink放到flow的函数中就可以重用
  def lineSink(filename: String): Sink[String, Future[IOResult]] =
    Flow[String].map(s => ByteString(s + "\n"))
      .toMat(FileIO.toPath(Paths.get("factorials.txt")))(Keep.right)

  //进行sink重用
  val sinkReUsed: Future[IOResult] = factorials.map(_.toString).runWith(lineSink("factorial2.txt"))

  //对source进行限流输出,每秒输出
  val sourceThrottle: Future[Done] = factorials
    .zipWith(Source(0 to 100))((num, idx) => s"$idx! = $num")
    .throttle(1, 1.second, 1, ThrottleMode.shaping)
    .runForeach(println)

  //具体实例
  final case class Author(handle: String)
  final case class Hashtag(name: String)
  final case class Tweet(author: Author, timestamp: Long, body: String) {
    def hashtags: Set[Hashtag] =
      body.split(" ").collect { case t if t.startsWith("#") => Hashtag(t) }.toSet
  }
  val akka = Hashtag("#akka")
  val author1 = Author("author1")
  val author2 = Author("author2")
  val author3 = Author("author3")
  val tweet1 = Tweet(author1,1L,"#akka is good")
  val tweet2 = Tweet(author2,2L,"#spark #storm is good")
  val tweet3 = Tweet(author1,3L,"#akka #spark is amazing")
  val tweet4 = Tweet(author3,4L,"#akka #play #framework is wonderful")
  val tweets: Source[Tweet, NotUsed] = Source(List(tweet1,tweet2,tweet3,tweet4))
  val authors: Source[Author, NotUsed] = tweets.filter(_.hashtags.contains(akka)).map(_.author)
  authors.runWith(Sink.foreach(println))

  //flatMap
  val hashtags = tweets.mapConcat(t => t.hashtags.toList).map(s => (s,1)).runWith(Sink.foreach(println))

  //broadcast Grape
  //Sinks (Subscribers) 和 Sources (Publishers)
  val g = RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
    //b: Builder[NotUsed],Graph builder
    import GraphDSL.Implicits._
    //把数据output两份
    val bcast = b.add(Broadcast[Tweet](2))
    tweets ~> bcast.in
    //第一份output的处理流程
    bcast.out(0) ~> Flow[Tweet].map(_.author) ~> Sink.foreach(println)
    //第二份output的处理流程
    bcast.out(1) ~> Flow[Tweet].mapConcat(_.hashtags.toList) ~> Sink.foreach(println)
    ClosedShape
  })
  g.run()


  def slowComputation(tweet: Tweet) = {
    Thread.sleep(2000)
    println(tweet)
    tweet.hashtags
  }
  //buffer中只是保留2个tweet,最新的tweet丢弃
  //Sink.ignore,表示忽略,在sink中不进行操作
  val sb = tweets.buffer(2, OverflowStrategy.dropNew).map(slowComputation).runWith(Sink.ignore)

  //在流的过程中输出处理了多少个tweet
  //使用materializer的executionContext
  import materializer.executionContext
  val count = Flow[Tweet].map(_ => 1)
  val sumSink = Sink.fold[Int, Int](0)(_ + _)
  val counterGraph = tweets.throttle(1, 1.second, 1, ThrottleMode.shaping).via(count).toMat(sumSink)(Keep.right)
  val sum = counterGraph.run()
  sum.foreach(c => println(s"Total tweets processed: $c"))
  //与上边的graph运行结果一致
  tweets.via(count).runWith(sumSink).foreach(c => println(s"Total tweets processed: $c"))
  tweets via count runWith sumSink foreach(c => println(s"Total tweets processed: $c"))

  val filterCounterGraph = tweets.filter(_.hashtags.contains(akka)).map(t => 1).toMat(sumSink)(Keep.right)
  filterCounterGraph.run().foreach(println)
  //与上边的graph运行结果一致
  tweets.filter(_.hashtags.contains(akka)).map(t => 1).runWith(sumSink)


  val consumer = Sink.foreach(println)
  val runnableGraph = MergeHub.source[String](perProducerBufferSize = 16).to(consumer)
  val toConsumer = runnableGraph.run()
  Source.single("Hello!").runWith(toConsumer)
  Source.single("Hub!").runWith(toConsumer)

}
