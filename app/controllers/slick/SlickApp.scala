package controllers.slick

import models.Table._

import play.Logger
import play.api.mvc.{Action, Controller}
import slick.dbio
import slick.driver.MySQLDriver.api._
import slick.jdbc.GetResult
import slick.jdbc.meta.MTable

import scala.async.Async._
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

/**
 * Created by cookeem on 15/12/11.
 */

/* Table mapping
 */
class SlickApp extends Controller {
  def slick1() = Action.async {
    val schema = (suppliers.schema ++ coffees.schema)
    println("schema.create.statements")
    schema.create.statements.foreach(println)
    println("schema.drop.statements")
    schema.drop.statements.foreach(println)
    val dropCmd = DBIO.seq((suppliers.schema ++ coffees.schema).drop)
    val setupCmd = DBIO.seq(
      // Create the tables, including primary and foreign keys
      (suppliers.schema ++ coffees.schema).create,
      // Insert some suppliers
      suppliers += Supplier(101, "Acme, Inc.",      "99 Market Street", "Groundsville", "CA", "95199"),
      suppliers += Supplier( 49, "Superior Coffee", "1 Party Place",    "Mendocino",    "CA", "95460"),
      suppliers += Supplier(150, "The High Ground", "100 Coffee Lane",  "Meadows",      "CA", "93966"),
      // Insert some coffees (using JDBC's batch insert feature, if supported by the DB)
      coffees ++= Seq(
        Coffee(0, "Colombian",         101, 7.99, 0, 0, "Colombian x"),
        Coffee(0, "French_Roast",      49, 8.99, 0, 0, "French_Roast x"),
        Coffee(0, "Espresso",          150, 9.99, 0, 0, "Espresso x"),
        Coffee(0, "Colombian_Decaf",   101, 8.99, 0, 0, ""),
        Coffee(0, "French_Roast_Decaf", 49, 9.99, 0, 0, ""),
        Coffee(0, "French_Roast_Decaf2", 150, 19.99, 0, 0, "French_Roast_Decaf2")
      )
    )

    val tables = Await.result(db.run(MTable.getTables), Duration.Inf).map(mt => mt.name.name.toLowerCase())
    if (tables.contains("coffees") && tables.contains("suppliers")) {
      val dropFuture = db.run(dropCmd)
      Await.result(dropFuture,Duration.Inf)
    }

    Logger.info("Drop suppliers and coffees tables success")
    val setupFuture: Future[Unit] = db.run(setupCmd)
    Logger.info("Create suppliers and coffees tables success")
    Future { Ok("Init suppliers and coffees tables successful")}
  }

  def slick2() = Action.async { implicit request =>
    //表查询
    db.run(suppliers.result).map(_.map {
      case c => s"$c"
    }.mkString("\n")).onSuccess{case s => println("表查询:\n"+s)}

    //关联查询
    val q1 = for {
      c <- coffees if c.price < 9.0
      s <- suppliers if s.id === c.supID
    } yield (s.name, s.street, c.name)
    println("关联查询:"+q1.result.statements.head)
    db.run(q1.result).map(_.map{ case (a,b,c) => s"$a, $b, $c \n"}).onSuccess{case t => t.foreach(x => println("关联查询:\n"+x))}

    //关联查询2
    val q2 = suppliers.join(coffees).on(_.id === _.supID).map{ case (s,c) => (s.name, c.name) }
    println("关联查询2:"+q2.result.statements.head)
    db.run(q2.result).map(_.map{ case (a,b) => s"$a, $b \n"}).onSuccess{case t => t.foreach(x => println("关联查询2:\n"+x))}

    //Query以Stream输出
    val q3 = for (c <- coffees) yield (c.name,c.price,c.sales)
    val p3 = db.stream(q3.result)
    println("普通查询:"+q3.result.statements.head)
    p3.foreach { case (a, b, c) => println(s"Stream输出: $a, $b, $c") }

    val joinFuture = db.run(q2.result).map(_.map {
      case (sname,cname) => s"suppliers_name: $sname , coffees_name: $cname"
    }.mkString("\n"))
    joinFuture.map(s => Ok(s))
  }

  def slick3 = Action {
    //Query样例
    val t1 = System.currentTimeMillis
    val sqlArr = ArrayBuffer[String]()
    val q1 = coffees.filter(_.supID === 101)
    sqlArr += "val q1 = coffees.filter(_.supID === 101)"
    sqlArr += q1.result.statements.head
    val q2 = coffees.drop(10).take(5)
    sqlArr += "val q2 = coffees.drop(10).take(5)"
    sqlArr += q2.result.statements.head
    val q3 = coffees.sortBy(_.name.desc.nullsFirst)
    sqlArr += "q3 = coffees.sortBy(_.name.desc.nullsFirst)"
    sqlArr += q3.result.statements.head
    val q4 = coffees.filter(t =>((t.name === "Colombian") || (t.name === "Espresso")))
    sqlArr += "val q5 = coffees.filter(t =>((t.name === \"Colombian\") || (t.name === \"Espresso\")))"
    sqlArr += q4.result.statements.head

    val crossJoin = for {
      (c, s) <- coffees join suppliers
    } yield (c.name, s.name)
    sqlArr += "crossJoin:"+crossJoin.result.statements.head
    val innerJoin = for {
      (c, s) <- coffees join suppliers on (_.supID === _.id)
    } yield (c.name, s.name)
    sqlArr += "innerJoin:"+innerJoin.result.statements.head
    val leftOuterJoin = for {
      (c, s) <- coffees joinLeft suppliers on (_.supID === _.id)
    } yield (c.name, s.map(_.name))
    sqlArr += "leftOuterJoin:"+leftOuterJoin.result.statements.head
    val rightOuterJoin = for {
      (c, s) <- coffees joinRight suppliers on (_.supID === _.id)
    } yield (c.map(_.name), s.name)
    sqlArr += "rightOuterJoin:"+rightOuterJoin.result.statements.head
    val fullOuterJoin = for {
      (c, s) <- coffees joinFull suppliers on (_.supID === _.id)
    } yield (c.map(_.name), s.map(_.name))
    sqlArr += "fullOuterJoin:"+fullOuterJoin.result.statements.head
    val monadicInnerJoin = for {
      c <- coffees
      s <- suppliers if c.supID === s.id
    } yield (c.name, s.name)
    sqlArr += "monadicInnerJoin:"+monadicInnerJoin.result.statements.head
    val zipWithIndexJoin = for {
      (c, idx) <- coffees.zipWithIndex
    } yield (c.name, idx)
    sqlArr += "zipWithIndexJoin:"+zipWithIndexJoin.result.statements.head
    db.run(zipWithIndexJoin.result).map(_.map{ case (a,b) => s"$a, $b \n"}).onSuccess{case t => t.foreach(x => println("zipWithIndexJoin:\n"+x))}

    val qx1 = coffees.filter(_.price < 8.0)
    val qx2 = coffees.filter(_.price > 9.0)
    val unionQuery = qx1 ++ qx2
    sqlArr += "unionQuery:"+unionQuery.result.statements.head

    val qt = (for {
      c <- coffees
      s <- c.supplier
    } yield (c, s)).groupBy(_._1.supID)
    val groupQuery = qt.map { case (supID, css) =>
      (supID, css.length, css.map(_._1.price).avg)
    }
    sqlArr += "groupQuery:"+groupQuery.result.statements.head
    val groutFuture = db.run(groupQuery.result)
    //Future输出Await
    val groupRs = Await.result(groutFuture, Duration.Inf)

    //删除
    val qt2 = coffees.filter(_.supID === 15)
    val queryDelete = qt2.delete
    sqlArr += "queryDelete:"+queryDelete.statements.head

    //插入并返回autoInc的ID
    val insertCoffee = (coffees returning coffees.map(_.cid)) += Coffee(0, "name_6", 150, 2.0, 5, 6, "")
    val insFuture = db.run(insertCoffee)
    var coffeeId = 0
    insFuture.onComplete{
      case Success(result) => coffeeId = result
      case Failure(e) => println("error:"+e.getMessage)
    }
    try {
      coffeeId = Await.result(insFuture, Duration.Inf)
    } catch {
      case e: Throwable => println("error:"+e.getMessage)
    }

    //直接插入
    val ic1 = coffees += Coffee(0, "name_1", 150, 2.0, 5, 6, "")
    val ic2 = coffees += Coffee(0, "name_2", 150, 2.0, 5, 6, "")
    db.run(ic1)
    db.run(ic2)
    //查看coffees的插入语法
    coffees.insertStatement
    //insertOrUpdate
    val qinsert = coffees.insertOrUpdate(Coffee(0, "name_2", 149, 2.0, 5, 6, ""))
    val a = db.run(qinsert)
    //update
    val updateQuery = coffees.filter(_.name === "Espresso").map(_.price).update(3.14)
    println("updateQuery:"+updateQuery.statements.head)
    db.run(updateQuery)
    //insertOrUpdate
    val updated = coffees.insertOrUpdate(Coffee(0, "name_1", 888, 3.14, 5, 6, ""))
    db.run(updated)

    //insertOrUpdate获取最新的AutoInc的ID
    var newid = 0
    val updated2 = (coffees returning coffees.map(_.cid)).insertOrUpdate(Coffee(0, "name_5", 150, 2.0, 5, 6, ""))
    val res: Future[Option[Int]] = db.run(updated2)
    res.onComplete{
      case Success(result) => {
        result match{
          case None => newid = 0
          case Some(i) => newid = i
        }
      }
      case Failure(e) => println("error:"+e.getMessage)
    }

    //直接运行SQL
    val insertSuppliers: dbio.DBIOAction[Unit, NoStream, Effect] = DBIO.seq(
      sqlu"insert into suppliers values(1, '1 Acme, Inc.', '99 Market Street', 'Groundsville', 'CA', '95199')",
      sqlu"insert into suppliers values(2, '2 Superior Coffee', '1 Party Place', 'Mendocino', 'CA', '95460')",
      sqlu"insert into suppliers values(3, '3 The High Ground', '100 Coffee Lane', 'Meadows', 'CA', '93966')"
    )
    db.run(insertSuppliers)

    def insertCoffees(c: Coffee): DBIO[Int] =
      sqlu"insert into coffees values (${c.cid}, ${c.name}, ${c.supID}, ${c.price}, ${c.sales}, ${c.total}, ${c.memo})"
    val insertCoffees2 = insertCoffees(Coffee(0,"coffee_01",101,2.2,4,5,""))
    val x1: Future[Int] = db.run(insertCoffees2)
    x1.onComplete{
      case Success(result) => {
        result match{
          case i: Int => println(i)
        }
      }
      case Failure(e) => println("error:"+e.getMessage)
    }

    //直接运行SQL并返回数据集
    val price = 20.0
    val tq1 = sql"""select c.cof_name, s.sup_name
      from coffees c, suppliers s
      where c.price < $price and s.sup_id = c.sup_id""".as[(String, String)].headOption
    val futurex = db.run(tq1)
    val Some(rs2) = Await.result(futurex, Duration.Inf)
    println(rs2)

    //直接运行SQL并返回数据集
    implicit val getCoffeeResult = GetResult(r => Coffee(r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<))
    val table = "coffees"
    val cof_id = 5
    val tq2 = sql"select * from #$table where cof_id < $cof_id".as[Coffee].headOption
    val futurex2 = db.run(tq2)
    val Some(rs3) = Await.result(futurex2, Duration.Inf)
    println(rs3)

    //Future序列的使用
    val (f1,f2,f3) = (Future {1}, Future {2}, Future {3})
    val f: Future[Seq[Int]] = Future.sequence(Seq(f1,f2,f3))
    f onComplete {
      case Success(r) => println(s"Sum: ${r.sum}")
      case Failure(e) => // Handle failure
    }

    //Scala异步处理
    async {
      val (f1,f2,f3) = (Future {1}, Future {2}, Future {3})
      val s = await {f1} + await {f2} + await {f3}
      println(s"Sum:  $s")
    } onFailure { case e => /* Handle failure */ }

    //获取tables列表
    val tbls = db.run(MTable.getTables)
    val tables = Await.result(tbls, Duration.Inf)
    println(s"Tables:")
    tables.foreach(t =>  println(t.name.name, "columns:"))

    println(s"Table columns:")
    tables.foreach(t => {
      val x = t.getColumns
      val cs = Await.result(db.run(x), Duration.Inf)
      cs.foreach{ c =>
        println(t.name.name,c.name,c.sqlTypeName)
      }
    })

    val t2 = System.currentTimeMillis
    val ts = Math.round((t2 - t1) * 100) / (100 * 1000.00)

    val sql = sqlArr.mkString(";\n")
    Ok(sql+"\nrun time:"+ts+" seconds")
  }

}
