package demo

import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks._

/**
  * Created by cookeem on 16/3/24.
  */
object ScalaSyntaxTest extends App {
  //循环中断
  breakable{
    for (i <- 1 to 100) {
      println(s"output is: $i")
      if (i > 10) break()
    }
  }

  //嵌套循环,嵌套判断
  for(i <- 1 to 3; j <- 1 to 5 if i != j) {
    println(s"output is: $i , $j")
  }

  //循环中嵌套变量赋值
  for(i <- 1 to 3; from = 4 - i; j <- from to 3) {
    println(s"output is: $i , $from, $j")
  }

  //yield
  for(c <- "Hello"; i <- 0 to 1) yield(c + i).toChar
  for(i <- 0 to 1; c <- "Hello") yield(c + i).toChar

  //变长参数
  def sum(args: Int*) = {
    var result = 0
    for (i <- args) {
      result += i
    }
    result
  }
  sum(1,3,5)
  //把range拆解成独立参数
  sum(1 to 5: _*)

  //变长参数递归,递归函数必须指定类型
  def recursiveSum(args: Int*): Int = {
    if (args.length == 0) 0
    else args.head + recursiveSum(args.tail: _*)
  }
  recursiveSum(1 to 5: _*)

  //懒值,使用的时候才计算
  lazy val var1 = scala.io.Source.fromFile("build.sbt1")
  try {
    var1.mkString
  } catch {
    case e: Throwable => println(s"Exception: ${e.getMessage}, ${e.getCause}")
  } finally {
    println(s"Here is finally")
  }

  //变长数组
  val ab1 = ArrayBuffer[Int](1 to 10: _*)
  ab1 += 100
  ab1 += 200
  val x = for (i <- ab1) yield 2*i
  val y = ab1.map(i => 2*i)

  //数组操作
  val ar1 = Array(3,4,1,6,9,4,5).map(i => (i, i*2, -i))
  ar1.sortBy(i => -i._1)
  ar1.sorted
  ar1.sortWith((a,b) => a._3 > b._3)
  val a = ar1.transform{case (a,b,c) => (a,b/2,-c)}
  ar1.zip(1 to 5)
  ar1.zipWithIndex
  scala.util.Sorting.quickSort(ar1)
  ar1.count{case (a,b,c) => a > b}
  var ar2 = Array[(Int,Int,Int)]()
  ar1.copyToArray(ar2)

  //多维数组
  type MatrixType = Array[Array[Int]]
  val matrix1: MatrixType = Array(Array(1,2,3), Array(3,4))
  val matrix = Array.ofDim[Int](3,4,5)
  matrix(0)(1)(2) = 5

  //Map操作
  val m1 = Map("a"->1,"b"->2,"e"->5,"d"->3)
  val m2 = Map(("a",1),("b",2),("c",5))
  m1.foreach{case (k,v) => println(s"$k->$v")}
  m1.toArray.sortWith((a,b) => a._2 < b._2).toMap
  val sm1 = scala.collection.SortedMap("a"->1,"b"->2,"e"->5,"d"->3,"c"->9)

  //Tuple操作
  val t1 = (101, 0.2, "Hello")
  val (tv1,tv2,_) = t1

  //Case Class,case class会自动创建伴生对象,不使用new也可以创建,适用于模式匹配
  case class Employee(var name: String, var age: Int, private val companys: String) {
    val companysList = companys.split(",")
  }
  val empolyee1 = Employee("zenghaijian", 37, "gmcc,cmcc")
  empolyee1.companysList
  empolyee1.name = "cookeem"

  //封闭类
  sealed case class SealClass(name: String)

  //Class,name参数具有getter/setter并且可以外部访问,nation参数因为class中没有调用因此不能外部访问
  class Daughter(var name: String, nation: String) {
    private var nickname = "" //私有变量
    var father = ""   //可变变量
    var mother = ""   //可变变量
    var age = 0       //可变变量
    def setNickname(s: String) = nickname = s
    def getNickname = nickname

    //嵌套类
    class School(var name: String, val address: String, var count: Int)
    //强制类型投影,只有使用强制类型投影,两个创建的不同的对象才能够相互投影条用
    var school: Daughter#School = _
    def setSchool(name: String, address: String, count: Int) = school = new School(name, address, count)

    //函数
    def setVal(s: String) = father = s
    def setVal(i: Int) = age = i

    //apply函数
    def apply(nickname: String): Daughter = {
      this.nickname = nickname
      this
    }

    //辅助构造器1,函数名称必须为this
    def this(name: String, nation: String, father: String, mother: String, age: Int) = {
      this(name, nation) //调用主构造器
      this.father = father
      this.mother = mother
    }

    //辅助构造器2,函数名称必须为this
    def this(name: String, nation: String, father: String, mother: String, age: Int, nickname: String) = {
      this(name, nation, father, mother, age)
      this.nickname = nickname
    }
    println(this.name)
  }

  val qingqing = new Daughter("zengyuqing", "china", "zenghaijian", "wuwenjing", 5) //辅助构造器1
  val qingqingx = new Daughter("zengyuqing", "china", "zenghaijian", "wuwenjing", 5, "zhuzhu") //辅助构造器2
  val qingqing1 = new Daughter("zengyuqing", "china")         //默认构造器
  val qingqing2 = new Daughter("zengyuqing", "china")("DoDo") //默认构造器+apply函数
  val qingqing3 = new Daughter("zengyuqing", "china", "zenghaijian", "wuwenjing", 5)("DoDo")  //辅助构造器1+apply函数
  qingqing.father = "zenghaijian"
  qingqing.mother = "wuwenjing"
  qingqing.age = 5
  qingqing.setNickname("zhuzhu")
  qingqing.getNickname
  qingqing.name

  qingqing.setSchool("xinfu","changgang road", 30)
  val school1 = qingqing.school
  //qingqing和qingqingx是两个不同的类,里边的School也是属于不同的类,不能直接赋值
  //之所以允许这样操作是因为school使用了强制类型投影:Daughter#School
  qingqingx.school = school1

  //object,相当于一个@singleton class,单例对象
  object Account {
    println(s"I am created!")
    private var lastNum: Int = 0
    def incr() = {
      lastNum = lastNum + 1
      println(s"lastNum is $lastNum")
      lastNum
    }
  }
  //object只会在调用的第一次创建
  Account.incr()
  Account.incr()

  //枚举对象
  object Weekday extends Enumeration {
    val mon = Value(1)
    val tue = Value(2)
    val wed = Value(3)
    val thu = Value(4)
    val fri = Value(5)
    val sat = Value(6)
    val sun = Value(7)
  }
  Weekday.values.foreach(println)

  // =>语法糖, 函数式编程, f是一个函数,f的输入是Int输出时String
  def fp1(i: Int)(f: Int => String) = {
    i * i
    f(i)
  }
  fp1(5){i => s"i is $i"}
  fp1(6)(i => i.toString)
  val f: (Int => String) =
    myInt => "The value of myInt is: " + myInt.toString()
  val fp1Result = fp1(5)(f)
  val j:(Int) => Int = {i => i * 2}

  //模式匹配
  case class Class1(name: String)
  case class Class2(address: String)
  case object Object1
  val xx = Class1("haijian")
  val yy = Object1

  val zz: String = xx match {
    case x@null => "null"       //常量匹配,赋值
    case Class1(name) => name   //unapply匹配,赋值
    case x: Class2 => "Class2"  //类型匹配,赋值
    case x@yy => "yy"           //变量值匹配,赋值
    case Class1("haijian") => "value" //常量匹配
    case _ => "other"           //泛匹配
  }

  var x1: Option[Int] = Some(5)
  x1 = None
  val z1 = x1 match {
    case Some(i) => i.toString
    case None => "0"
  }

  //中缀表达式
  case class Zhongzui(name: String, age: Int)
  val x2 = Zhongzui("cookeem", 37)
  x2 match {
    case x@"cookeem" Zhongzui 37 => "cookeem, 37" //中缀表达式,包含两个参数可以使用中缀表达式
    case x: Zhongzui => "Zhongzui"
    case _ => "other"
  }

  //嵌套函数
  def multiple(v1: Int, v2: Int, v3: Int) = {
    //嵌套的本地函数外部不可见
    def myAdd(x: Int, y: Int) = x + y
    val z1 = myAdd(v1,v2)
    myAdd(z1, v3)
  }

  //函数定义
  val incr1 = (x: Int) => x + 1 //Function1 1个参数的函数
  val incr2: (Int, Int) => Int = (x: Int, y: Int) => x + y  //Function2 2个参数的函数
  val incr3: (Int, Int) => Int = _+_  //节省参数用法
  val incr4 = (_:Int) + (_:Int) //节省参数写法2
  def incr5(x: Int)(y: Int)(z: Int) = x + y + z //柯里化
  incr5(4)(5)(6)
  def incr5x: Int => Int => Int => Int = x => y => z => x + y + z //柯里化写法2
  incr5x(4)(5)(6)
  val incr6: (Int) => (Int) => (Int) => Int = incr5 _   //函数赋值到别的函数
  incr6(4)(5)(6)
  incr6(4){5}{6}        //当函数()中只有一个参数的时候可以使用{}
  val incr7: (Int, Int, Int) => Int = incr5(_)(_)(_)  //函数赋值到别的函数
  incr7(4,5,6)  //注意,柯里化被合并掉了
  val incr8: (Int) => (Int) => Int = incr5(4)_  //柯里化参数预赋值
  incr8(5)(6)
  val incr9: (Int) => Int = incr4(_,5)  //同理部分赋值
  incr9(4)

  //闭包,map中 x => x 称为闭包
  "hello hai jian I am cool".split(" ").map(x => x.toUpperCase())

  //高阶函数,函数的参数带有函数称为高阶函数
  def gaojieFun(x: Int)(f: Int => String) = {
    f(x*2)
  }
  gaojieFun(5){x => s"output is $x"}

  //偏函数,所有由case x => .. 组成的函数称为偏函数
  def p1: PartialFunction[Int, String] = {
    case x if x == 1 => "is one"
    case x if x == 2 => "is two"
    case y if y > 2 => "greater than two"
  }
  p1(1)
  p1.isDefinedAt(0)
  p1(-1)
  Array(0,1,2,3).collect(p1)  //使用偏函数作为参数
  def p2: PartialFunction[String, Int] = {
    case x if x == "-1" => -1
  }
  //p1: PartialFunction[T1,T2] p2: PartialFunction[T2,T1]才能进行compose和andThen
  val p3: (String) => String = p1.compose(p2)                 //组合成一个函数p1(p2(x))
  val p4: PartialFunction[Int, Int] = p1.andThen(p2)          //组合成一个偏函数p2(p1(x))

  //类的继承
  class MyClass1(var name: String)
  //伴生对象,object不能传递参数,class可以传递参数.伴生对象和伴生类的private可以互访
  object MyClass2 {
    private var myVal = "orz"
    //通过apply方法,让object支持参数
    def apply(s: String) = {
      myVal = s
    }
    def desc(name: String, address: String) = s"my name is $name, my address is $address"
  }
  //带有参数的类继承
  class MyClass2(var nickname: String, var address: String) extends MyClass1(nickname) {
    val desc = MyClass2.desc(nickname, address)
    def myVal2 = MyClass2.myVal
  }
  val mc2 = MyClass2("this is myVal")  //通过apply让object支持参数
  val mc2x = new MyClass2("haijian", "guangzhou") //创建伴生类
  mc2x.myVal2 == "this is myVal"

  //抽象类,可以不提供具体实现甚至具体数值
  abstract class AbstractClass1 {
    type T            //定义类型
    val init: T       //定义常量
    var current: T    //定义变量
    def f: Int => T   //定义函数
  }
  //特质与抽象类类似,trait不能带有参数
  trait TraitClass1 {
    type T            //定义类型
    val init: T       //定义常量
    var current: T    //定义变量
    def f: Int => T   //定义函数
  }
  //继承trait
  class TraitClass3 extends TraitClass1 {
    def f: Int => T = {x => s"the int is $x".asInstanceOf[T]} //class必须实现抽象方法
    type T = String //class必须指定抽象类型
    val init: T = "init"  //class必须指定抽象常量
    var current: T = "current"  //class必须指定抽象变量
  }
  //继承并重载trait
  class TraitClass4 extends TraitClass1 {
    def f: Int => T = {x => s"the int is $x"}
    override type T = String
    override val init: T = "init"
    override var current: T = "current"
  }

  //Scala的AnyRef相当于Java的java.lang.Object
  val myTraitClass: AnyRef = new TraitClass3

  //类型转换
  //显式类型转换
  1.asInstanceOf[Double]
  //隐式类型转换,当函数的参数中有对应的类型,会进行隐式转换
  implicit def int2String(i: Int): String = s"int is $i"
  def myFunc001(s: String) = s.toUpperCase()
  myFunc001(5)
  //隐式参数,只要类型符合,就会使用隐式参数,隐式参数一般放在柯里化的最后一个括号中
  implicit val initVal = 100
  def myFunc002(i: Int)(implicit j: Int) = i + j
  myFunc002(5)
  myFunc002(5)(20)

  //类型的类型界定,类型界定<%已经deprecated
  class TypeClass1[T <: Any](val1: T)

  val typeClass1 = new TypeClass1[Int](1)
  val typeClass2 = new TypeClass1[String]("cookeem")

  //协变与逆协变
  class RootClass
  class ParentClass extends RootClass
  class ChildClass extends ParentClass
  class TypeClass2[T](val1: T) {
    def f1[R <: T](r: R) = r.getClass //类型协变,R必须是T的子类
    def f2[R >: T](r: R) = r.toString //类型逆协变,R可以是T的父类
  }
  val tc2 = new TypeClass2[ParentClass](new ParentClass)
  tc2.f1[ChildClass](new ChildClass)  //必须是ParentClass的子类
  tc2.f2[RootClass](new RootClass)    //可以是ParentClass的父类
  tc2.f2[ParentClass](new ChildClass) //可以是ParentClass的子类

  //+A -A
  class TypeClass3[+A]
  val tc3: TypeClass3[AnyRef] = new TypeClass3[String]
  class TypeClass4[-A]
  val tc4: TypeClass4[String] = new TypeClass4[AnyRef]

  //泛型
  class Foo1[A,B](name: A, age: B) //泛型Class
  val foo1 = new Foo1[String, Int]("haijian", 37)
  def flxFunc1[A,B](x:A, y:B) = s"$x is ${x.getClass}, $y is ${y.getClass}" //泛型Function
  val flxFunc2 = flxFunc1[String,Int] _   //泛类型函数

  //单例类
  class RepeatClass1(var name: String) {
    //父类的函数设置成this.type类型之后,子类就可以进行继承
    def f1: this.type = {
      name = name.toLowerCase
      this
    }

    def f2 = {
      name = name.toUpperCase
      this
    }
  }
  val rc1 = new RepeatClass1("haijian")
  rc1.f2.f1.f2.name

  class RepeatClass2(var name2: String) extends RepeatClass1(name2) {
    def f3 = {
      name = s"my name is $name2"
      this
    }
  }
  val rc2 = new RepeatClass2("haijian")
  rc2.f3.f1.f2.name
  rc2.f1.f3.name  //因为父类的f1定义了this.type,单类实例,生成的实例可以调用子类的函数
  //rc2.f2.f3.name  因为父类的f2没有定义this.type, 不能进行这种操作

  //类型别名
  type TypeAlias = Array[Map[String, Int]]
  val myTypeVal: TypeAlias = Array(Map("a"->1,"b"->2), Map("c"->3), Map("d"->4))
  myTypeVal.getClass //Class[_ <: TypeAlias] = class [Lscala.collection.immutable.Map;

  //包引入
  import java.util.{HashMap => _,_}   //排除HashMap
  import scala.collection.immutable.HashMap
  val hm: HashMap[String, Int] = HashMap("a" -> 1, "b" -> 2)
  import java.util.{LinkedHashMap => LHMap}  //包别名
  val lhmap: LHMap[String, Int] = new LHMap()
  lhmap.put("a",1)
  lhmap.put("b",2)
  type Mylhmap = LHMap[String, Int]   //类型别名
  classOf[Mylhmap]
  val mylhmap = new Mylhmap()

  //进程管理
  import sys.process._
  val lsResult = "ls -al" !!
  val lsResultInt: Int = "ls -al" !   //0表示成功执行
  val lsGrepResult = "ls -al" #| "grep a" !!    //#有特殊含义,进程管道

  //正则表达式
  val regex1 = "[0-9]+".r
  val regMatchStr = regex1.findAllIn("hello 89 is 89").mkString
  val regReplaceStr = regex1.replaceAllIn("hello 98 is 100 - 2", "int").mkString
  //正则表达式组
  val regex2 = "([0-9]+) ([a-z]+)".r
  val regex2(myNum, myStr) = "2 two" // myNum=2, myStr = two

  //特质
  trait MyTraitClass1 {
    type T            //定义类型,可以不实现
    val init: T       //定义常量,可以不实现
    var current: T    //定义变量,可以不实现
    def f: Int => T   //定义函数,可以不实现
    def mf(s: String) = "upper case is: "+s.toUpperCase //特质带有具体实现
  }
  class IsTraitClass1 extends MyTraitClass1 {
    def f: Int => T = {x => s"the int is $x".asInstanceOf[T]}
    type T = String
    val init: T = "init"
    var current: T = "current"
    def mf2(s: String) = "mf2: " + mf(s)
  }
  val isTraitClass1 = new IsTraitClass1
  isTraitClass1.f(5)
  isTraitClass1.mf("haijian")
  isTraitClass1.mf2("haijian")
  class ClassForMix1 {
    def myPrint(s: String) = {
      println(s"ClassForMix1 myPrint $s")
    }
  }
  //只有trait可以用于with混合继承
  class MixClass1 extends ClassForMix1 with MyTraitClass1 {
    def f: Int => T = {x => s"the int is $x".asInstanceOf[T]}
    type T = String
    val init: T = "init"
    var current: T = "current"
  }
  val mixClass1 = new MixClass1
  mixClass1.f(5)  //来源于MyTraitClass1的方法
  mixClass1.myPrint("#orz#")  //来源于ClassForMix1的方法

  //特质继承
  trait Logged {
    def log(s: String) = println(s)
  }
  trait GMTLogged extends Logged {
    //重载特质的方法
    override def log(s: String) = {
      val date = (new Date()).toGMTString
      println(s"$date @ $s")
    }
  }
  trait LocalLogged extends Logged {
    //重载特质的方法
    override def log(s: String) = {
      val date = (new Date()).toLocaleString
      println(s"$date @ $s")
    }
  }
  //直接创建特质对象
  val mixLogged = new MixClass1 with GMTLogged
  mixLogged.log("is log")

  //特质继承2
  trait Logger {
    val currentTime: String
    def log(s: String)  //抽象方法
    def info(s: String) = log(s"[INFO] $s")   //有实现的方法
    def warn(s: String) = log(s"[WARN] $s")   //有实现的方法
    def error(s: String) = log(s"[ERROR] $s")   //有实现的方法
  }
  //在特质中重写抽象方法
  trait GMTLogger extends Logger {
    //必须定义abstract,因为super.log并未定义
    abstract override def log(s: String) = super.log(s"${(new Date()).toGMTString} @ $s")
  }
  class MixClass2 extends Logger {
    val currentTime = (new Date()).toGMTString
    def log(s: String) = println(s"### $s")
  }

  //trait自身类型,限制trait只能有应用于对应类型的子类
  trait MyLog {
    this: Throwable => {
      def log(s: String) = println(s"[LOG] error! $s")
    }
  }
  val myLog = new ClassCastException with MyLog

  //抽取器apply与unapply,unapply应用于模式匹配或者模式赋值
  object Email {
    def apply(name: String, address: String): String = s"$name@$address"
    def unapply(email: String): Option[(String, String)] = {
      val parts = email.split("@")
      if (parts.length == 2) {
        Some((parts(0),parts(1)))
      } else {
        None
      }
    }
  }
  val email = Email("cookeem", "qq.com")
  //unapply应用于模式匹配
  email match {
    case Email(name, address) => s"name is $name, address is $address"
  }
  //unapply应用于模式赋值
  val Email(emailName,emailAddress) = Email("cookeem", "139.com")
  "cookeem#qq.com" match {
    case Email(name, address) => s"name is $name, address is $address"
    case x: Any => s"none ${x.getClass}"
  }

  //伴生对象apply
  class Company(name: String, address: String, userCount: Int)
  object Company {
    def apply(name: String, address: String, userCount: Int) = new Company(name, address, userCount)
  }
  val gmccCompany = Company("gmcc", "zhujiangxilu", 2000)
}
