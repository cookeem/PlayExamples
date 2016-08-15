package demo

import java.util.concurrent.{TimeUnit, Executors}
import java.util.Date

import scala.concurrent.{Future, ExecutionContext}
import scala.util.Random

/**
  * Created by cookeem on 16/7/25.
  */
object ExecutionTest1 extends App {
  implicit val ec = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(20))
  val r = new Random()
  1 to 100 foreach{i =>
    Future(i).map{x =>
      Thread.sleep((r.nextInt(20)+1)*50)
      println(s"### ${new Date} # $x")
    }
  }
//  ec.shutdown()
//  ec.awaitTermination(2, TimeUnit.SECONDS)
}
