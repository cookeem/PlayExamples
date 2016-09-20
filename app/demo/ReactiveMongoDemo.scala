package demo

import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import play.api.libs.json._
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api._
import reactivemongo.api.commands.Command
import reactivemongo.bson._
import reactivemongo.play.json._
import reactivemongo.play.json.collection._
import reactivemongo.play.json.collection.JsCursor._

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by cookeem on 16/9/20.
  */
object ReactiveMongoDemo extends App {
  implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(50))

  case class Person(firstName: String, lastName: String, age: Int)
  implicit def personHandler = Macros.handler[Person]
  //  implicit def personWriter: BSONDocumentWriter[Person] = Macros.writer[Person]
  //  implicit def personReader: BSONDocumentReader[Person] = Macros.reader[Person]
  val cookeem = Person("zeng", "haijian", 37)
  val cookeemNew = Person("zeng", "haijian", 38)
  val faith = Person("wu", "wenjing", 31)

  val mongoUri = "mongodb://localhost:27017/local"
  val driver = MongoDriver()
  val parsedUri = MongoConnection.parseURI(mongoUri)
  val connection = parsedUri.map(driver.connection)
  val futureConnection = Future.fromTry(connection)
  val newdb = futureConnection.flatMap(_.database("newdb"))
  val personCollection = newdb.map(_.collection[BSONCollection]("person"))

  //insert
  personCollection.flatMap(_.insert(cookeem).map(ws => ws.n)).foreach(println)
  personCollection.flatMap(_.insert(faith).map(_ => {}))

  //insert
  personCollection.flatMap(_.insert(
    BSONDocument(
      "firstName" -> "Stephane",
      "lastName" -> "Godbillon",
      "age" -> 29
    )
  ))
  //bulkInsert
  personCollection.flatMap(_.bulkInsert(ordered = false)(
    document("name" -> "document1"),
    document("name" -> "document2"),
    document("name" -> "document3"))
  )

  //嵌套查询
  personCollection.flatMap(
    _.find(
      document(
        "name" -> document("$type" -> 2)
      )
    ).cursor[JsObject]().jsArray()
  ).foreach{jsarray =>
    jsarray.as[Seq[JsValue]].foreach(println)
  }


  //update
  val selector = document(
    "firstName" -> cookeem.firstName,
    "lastName" -> cookeem.lastName
  )
  personCollection.flatMap(_.update(selector,cookeemNew,multi = true).map(_.n))

  //find
  personCollection.flatMap(_.find(document("age" -> 31)).cursor[Person]().collect[List]()).foreach(println)

  //delete
  personCollection.flatMap(_.remove(document("name" -> "document2"), firstMatchOnly = true)).foreach(wr => println(wr.n))

  //json support
  val jsonCollection = newdb.map(_.collection[JSONCollection]("person"))
  jsonCollection.flatMap(_.find(Json.obj("age" -> 31)).cursor[JsObject]().jsArray()).foreach{jsarray =>
    jsarray.as[Seq[JsValue]].foreach(println)
  }

  //akka streaming
  //materializer都是在actorSystem中运行
  implicit val system = ActorSystem("QuickStart")
  //stream都是在materializer中运行,必须进行implicit
  implicit val materializer = ActorMaterializer()
  val futureResult = personCollection.flatMap(_.find(document()).cursor[JsObject]().collect[List]())
  Source.fromFuture(futureResult).mapConcat(js => js).runForeach(println)

  //runCommand
  newdb.foreach {db =>
    val commandDoc = BSONDocument("find" -> "users")
    val futureDoc = db.runCommand(Command.run(BSONSerializationPack).rawCommand(commandDoc)).one[BSONDocument]
    futureDoc.foreach { doc =>
      val json = Json.toJson(doc)
      println(Json.prettyPrint((json \ "cursor" \ "firstBatch").getOrElse(JsNull)))
    }
  }
}
