package actor

import akka.event.Logging
import akka.{Done, NotUsed}
import akka.actor.{Props, ActorRef, ActorSystem}
import akka.stream._
import akka.stream.scaladsl._

import scala.concurrent.Future


/**
 * Created by cookeem on 16/3/10.
 */
object StreamTest2 extends App {
  //materializer都是在actorSystem中运行
  implicit val system = ActorSystem("QuickStart")
  //stream都是在materializer中运行,必须进行implicit
  implicit val materializer = ActorMaterializer()
  //必须import一个executionContext
  import materializer.executionContext
  val source = Source(1 to 10)
  val sink = Sink.fold[Int, Int](0)(_ + _)
  // connect the Source to the Sink, obtaining a RunnableGraph
  val runnable: RunnableGraph[Future[Int]] = source.toMat(sink)(Keep.right)
  // materialize the flow and get the value of the FoldSink
  val sum = runnable.run()
  // 与下边相同
  val sum2 = source.runWith(sink)

  // 创建Source
  // Create a source from an Iterable
  Source[Int](List(1, 2, 3))
  // Create a source from a Future
  Source.fromFuture(Future.successful("Hello Streams!"))
  // Create a source from a single element
  Source.single("only one element")
  // an empty source
  Source.empty

  // 创建Sink
  Sink.fold[Int, Int](0)(_ + _)
  // 返回第一个value
  Sink.head
  // 返回最后一个value
  Sink.last
  // 不做任何事情
  Sink.ignore
  // 打印每一个value
  Sink.foreach(println)

  val x: Source[Int, NotUsed] = Source(1 to 6).via(Flow[Int].map(_ * 2))
  val y: Source[Int, NotUsed] = Source(1 to 6)
  //下边两句结果相同,输出对象类型不同
  val rg1: RunnableGraph[NotUsed] = Source(1 to 6).via(Flow[Int].map(_ * 2)).to(Sink.foreach(println(_)))
  rg1.run()
  val rg2: RunnableGraph[Future[Done]] = Source(1 to 6).via(Flow[Int].map(_ * 2)).toMat(Sink.foreach(println(_)))(Keep.right)
  rg2.run()

  //最简单方式
  val rg3: RunnableGraph[NotUsed] = Source(1 to 6).map(_ * 2).to(Sink.foreach(println))
  rg3.run()
  val sink2: Sink[Int, NotUsed] = Flow[Int].map(_ * 2).to(Sink.foreach(println(_)))
  val rg4: RunnableGraph[NotUsed] = Source(1 to 6).to(sink2)
  rg4.run()

  // sink复制asloTo
  val otherSink: Sink[Int, NotUsed] =
    Flow[Int].alsoTo(Sink.foreach(t => println("sink:"+t))).to(Sink.foreach(t => println("other sink:"+t)))
  val rg5: RunnableGraph[NotUsed] = Source(1 to 6).to(otherSink)
  rg5.run()
  //runReduce[U >: Out](f: (U, U) ⇒ U) >:表示类型约束,U表示变量
  //def a(f: Float => String): String = f.toString, 表示参数和输出转换
  Source(1 to 100).runReduce[Int]((a,b) => a+b).foreach(println)
  Source(1 to 100).runFold(0)((a,b) => a+b).foreach(println)

  //Flow的Fuse操作,Flow相当于一个执行函数
  val flow = Flow[Int].map(_ * 2).filter(_ > 500)
  //使用Fusing接管Flow
  val fused = Fusing.aggressive(flow)
  //Source.fromIterator(() => Iterator.from(0)),创建一个从0开始的没有结尾的数据流
  Source.fromIterator(() => Iterator.from(0)).via(fused).take(20)

  //表示:Source(List(1, 2, 3)).map(_ + 1)这个部分代码由一个actor执行,
  //map(_ * 2).to(Sink.ignore)部分代码有其他actor执行
  Source(List(1, 2, 3)).map(_ + 1).async.map(_ * 2).to(Sink.ignore)

  //  • Fan-out
  //  Broadcast[T] – (1 input, N outputs) given an input element emits to each output
  //  Balance[T] – (1 input, N outputs) given an input element emits to one of its output ports
  //  UnzipWith[In,A,B,...] – (1 input, N outputs) takes a function of 1 input that given a value for each input emits N output elements (where N <= 20)
  //  UnZip[A,B] – (1 input, 2 outputs) splits a stream of (A,B) tuples into two streams, one of type A and one of type B
  //
  //  • Fan-in
  //  Merge[In] – (N inputs , 1 output) picks randomly from inputs pushing them one by one to its output
  //  MergePreferred[In] – like Merge but if elements are available on preferred port, it picks from it, otherwise randomly from others
  //  ZipWith[A,B,...,Out] – (N inputs, 1 output) which takes a function of N inputs that given a value for each input emits 1 output element
  //  Zip[A,B] – (2 inputs, 1 output) is a ZipWith specialised to zipping input streams of A and B into an (A,B) tuple stream
  //  Concat[A] – (2 inputs, 1 output) concatenates two streams (first consume one, then the second one)

  //Graph,当fan-in有多个input或者fan-out有多个output的时候使用Graph
  val graph1 = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
    import GraphDSL.Implicits._
    val in = Source(1 to 10)
    val out = Sink.foreach(println)
    //builder.add表示在图中添加fan-in或者fan-out节点
    val bcast2 = builder.add(Broadcast[Int](2))
    val bcast3 = builder.add(Broadcast[Int](3))
    val merge = builder.add(Merge[Int](4))
    val f1, f2, f3, f4, f5, f6, f7,f8 = Flow[Int].map(_ + 10)
    //以下代码相当于一个图,fx表示给数值+10
    // ~> 相当于via <~反向via
    in ~> f1 ~> bcast3 ~> f2 ~> merge ~> f3 ~> out
    bcast3 ~> f4 ~> merge
    bcast3 ~> f5 ~> bcast2
    bcast2 ~> f6 ~> merge
    merge <~ f7 <~ bcast2
    ClosedShape
  })
  graph1.run()

  //调用外部sink的graph,在Graph中控制入度in和出度out
  val topHeadSink = Sink.head[Int]
  val bottomHeadSink = Sink.last[Int]
  val sharedDoubler = Flow[Int].map(_ * 2)
  val graph2 = RunnableGraph.fromGraph(GraphDSL.create(topHeadSink, bottomHeadSink)((_, _)) { implicit builder =>
    (topHS, bottomHS) =>
      import GraphDSL.Implicits._
      val broadcast = builder.add(Broadcast[Int](2))
      //broadcast节点的in入度
      Source(1 to 5) ~> broadcast.in
      //broadcast节点的out链接topHS节点的in
      broadcast.out(0) ~> sharedDoubler ~> topHS.in
      broadcast.out(1) ~> sharedDoubler ~> bottomHS.in
      //上图相当于
      //Source(1 to 5) ~> broadcast ~> sharedDoubler ~> topHS
      //broadcast ~> sharedDoubler ~> bottomHS
      ClosedShape
  })
  val (v1,v2) = graph2.run()
  v1.foreach(println)
  v2.foreach(println)

  //组合多个图
  val pickMaxOfThree = GraphDSL.create() { implicit b =>
    import GraphDSL.Implicits._
    //比较两个int,out为比较大的那个int
    val zip1 = b.add(ZipWith[Int, Int, Int](math.max _))
    val zip2 = b.add(ZipWith[Int, Int, Int](math.max _))
    zip1.out ~> zip2.in0
    //zip1有两个输入in0和in1,一个输出out;
    //zip2有两个输入in0和in1,一个输出out;
    //把zip1.in0,zip1.in1以及zip2.in1聚合成一个图
    //表示这个是开放图,没有关闭in和out
    UniformFanInShape(zip2.out, zip1.in0, zip1.in1, zip2.in1)
  }
  val resultSink = Sink.head[Int]
  val g = RunnableGraph.fromGraph(GraphDSL.create(resultSink) { implicit b =>
    sink =>
      import GraphDSL.Implicits._
      // importing the partial graph will return its shape (inlets & outlets)
      val pm3 = b.add(pickMaxOfThree)
      //pm3.in(x)表示把数据放到图的第几个参数,对应上边的UniformFanInShape的inx
      Source.single(1) ~> pm3.in(0)
      Source.single(2) ~> pm3.in(1)
      Source.single(3) ~> pm3.in(2)
      pm3.out ~> sink.in
      //表示这个是闭合图,RunnableGraph都是闭合图
      ClosedShape
  })
  val max: Future[Int] = g.run()
  max.foreach(println)

  //Source Graph,只有一个out,没有in
  val pairs = Source.fromGraph(GraphDSL.create() { implicit b =>
    import GraphDSL.Implicits._
    // prepare graph elements
    val zip = b.add(Zip[Int, Int]())
    def ints = Source(1 to 10)
    // 把单数放到Tuple._1,双数放到Tuple._2,zip聚合成一个Tuple
    ints.filter(_ % 2 != 0) ~> zip.in0
    ints.filter(_ % 2 == 0) ~> zip.in1
    SourceShape(zip.out)
  })
  val firstPair = pairs.runWith(Sink.foreach(println))

  //Flow Graph,有一个in,有一个out
  val pairUpWithToString: Flow[Int, (Int, String), NotUsed] =
    Flow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._
      // prepare graph elements
      val broadcast = b.add(Broadcast[Int](2))
      val zip = b.add(Zip[Int, String]())
      // connect the graph
      broadcast.out(0).map(identity) ~> zip.in0
      broadcast.out(1).map(_.toString+":string") ~> zip.in1
      // expose ports
      FlowShape(broadcast.in, zip.out)
    })
  //Flow也可以使用runWith
  pairUpWithToString.runWith(Source(0 to 10), Sink.foreach(println))
  //SourceShape, SinkShape, FlowShape for simpler shapes,
  //• UniformFanInShape and UniformFanOutShape for junctions with multiple input (or output) ports
  //  of the same type,
  //• FanInShape1, FanInShape2, ..., FanOutShape1, FanOutShape2, ... for junctions with multiple
  //input (or output) ports of different types.

  //嵌套graph
  val nestedGraph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    val A: Outlet[Int] = builder.add(Source(1 to 10)).out
    val B: UniformFanOutShape[Int, Int] = builder.add(Broadcast[Int](2))
    val C: UniformFanInShape[Int, Int] = builder.add(Merge[Int](2))
    val D: FlowShape[Int, Int] = builder.add(Flow[Int].map(_ + 1))
    val E: UniformFanOutShape[Int, Int] = builder.add(Balance[Int](2))
    val F: UniformFanInShape[Int, Int] = builder.add(Merge[Int](2))
    val G: Inlet[Any] = builder.add(Sink.foreach(println)).in
              C <~ F
    A ~> B ~> C ~> F
         B ~> D ~> E ~> F
                   E ~> G
    ClosedShape
  })
  nestedGraph.run()

  val partial = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    val B = builder.add(Broadcast[Int](2))
    val C = builder.add(Merge[Int](2))
    val E = builder.add(Balance[Int](2))
    val F = builder.add(Merge[Int](2))
    C <~ F
    B ~> C ~> F
    B ~> Flow[Int].map(t => {
      println(s"B ~> Flow[Int]: $t")
      t + 1
    }) ~> E ~> F
    FlowShape(B.in, E.out(1))
  }.named("partial")
  Source(0 to 10).via(partial).to(Sink.foreach(println)).run()
  Flow[Int].map(_ * 2).runWith(Source(0 to 10),Sink.foreach(println))

  //更简单的fan-in和fan-out
  val sourceOne = Source(1 to 5)
  val sourceTwo = Source(101 to 110)
  //简单聚合
  val merged = Source.combine(sourceOne, sourceTwo)(t => Merge(t))
  merged.runForeach(println)
  merged.runWith(Sink.fold(0)(_ + _)).foreach(println)
  //简单zip,把两个流聚合成tuple
  val zipped = sourceOne.zip(sourceTwo)
  zipped.runForeach(println)
  //简单zipWith,把两个流进行聚合运算,求和
  val zipwithed = sourceOne.zipWith[Int, Int](sourceTwo)((a,b) => a+b)
  zipwithed.runForeach(println)
  //broadcast
  sourceOne.alsoTo(Sink.foreach(t => println(s"alsoTo($t)"))).runForeach(t => println(s"runForeach $t"))

  Source.combine(Source(1 to 10), Source(101 to 110)){t => Merge(t)}.runForeach(println)

  val sinkX1 = Sink.foreach[Int]{t => println(s"s1: $t")}
  val sinkX2 = Sink.foreach[Int]{t => println(s"s2: $t")}
  val sinkX = Sink.combine(sinkX1, sinkX2){Broadcast[Int](_)}
  Source(1 to 10).to(sinkX).run()

  implicit val adapter = Logging(system, "customLogger")
  Source(1 to 10).log("custom")

}
