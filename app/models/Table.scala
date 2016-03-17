package models

import slick.driver.MySQLDriver.api._

/**
 * Created by cookeem on 15/12/11.
 */
object Table {
  case class Supplier(id: Int, name: String, street: String, city: String, state: String, zip: String)
  class Suppliers(tag: Tag) extends Table[Supplier](tag, "SUPPLIERS") {
    def id = column[Int]("SUP_ID", O.PrimaryKey)
    def name = column[String]("SUP_NAME")
    def street = column[String]("STREET")
    def city = column[String]("CITY")
    def state = column[String]("STATE")
    def zip = column[String]("ZIP")
    def * = (id, name, street, city, state, zip) <> ((Supplier.apply _).tupled, Supplier.unapply _)
  }
  lazy val suppliers = TableQuery[Suppliers]

  case class Coffee(cid: Int, name: String, supID: Int, price: Double, sales: Int, total: Int, memo: String)
  class Coffees(tag: Tag) extends Table[Coffee](tag, "COFFEES") {
    def cid = column[Int]("COF_ID", O.AutoInc, O.PrimaryKey)
    def name = column[String]("COF_NAME", O.Default(""))
    def supID = column[Int]("SUP_ID", O.Default(0))
    def price = column[Double]("PRICE", O.Default(0))
    def sales = column[Int]("SALES", O.Default(0))
    def total = column[Int]("TOTAL", O.Default(0))
    def memo = column[String]("MEMO", O.Default(""))
    def * = (cid, name, supID, price, sales, total, memo) <> ((Coffee.apply _).tupled, Coffee.unapply _)
    def supplier = foreignKey("SUP_FK", supID, suppliers)(_.id)
    def idx = index("idx_name_supid", (name, supID), unique = true)
  }

  lazy val coffees = TableQuery[Coffees]

  lazy val db = Database.forConfig("mysqlDB")
}
