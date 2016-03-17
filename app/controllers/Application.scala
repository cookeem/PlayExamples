package controllers

import java.io.{FileInputStream, FileOutputStream, File}
import javax.inject.{Singleton, Inject}

import akka.NotUsed
import akka.actor._
import akka.stream.{IOResult, ActorMaterializer}
import akka.stream.scaladsl._
import akka.util.ByteString

import play.api._
import play.api.db._
import play.api.http.HttpEntity
import play.api.libs.Comet
import play.api.libs.iteratee.Concurrent.Channel
import play.api.libs.iteratee.{Enumeratee, Enumerator, Concurrent}
import play.api.libs.json._
import play.api.libs.streams.{ActorFlow, Streams}
import play.api.libs.ws._
import play.api.mvc._
import play.api.mvc.BodyParsers.parse.file
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.concurrent.duration._

//运行纯https模式
//sbt run -Dhttps.port=9443 -Dhttp.port=disabled

case class Customer(name: String)
case class Order(title: String, price: Double)

//保证只是启动一个实例
@Singleton
class Application @Inject() (db: DBApi, ws: WSClient) extends Controller {
  //必须在controllers中声明materialize
  implicit val system = ActorSystem("system")
  implicit val materializer = ActorMaterializer()

  val notFound = NotFound
  val pageNotFound = NotFound(<h1>Page not found</h1>).as(HTML)
  val badRequest = BadRequest(views.html.index("BadRequest"))
  val oops = InternalServerError("Oops")
  val anyStatus = Status(488)("Strange response type")
  val xml = Action(Ok(<message>Hello World!</message>).as(XML))
  val json = Action(Ok("""{"name":"haijian","result":"ok"}""").as(JSON))
  val header = Action(Ok(<message>This is header</message>).withHeaders(CACHE_CONTROL -> "max-age=3600",ETAG -> "xx").as(TEXT))

  val logger = Logger(this.getClass())

  val test = Action { request =>
    ok(request)
  }

  def ok(request: Request[AnyContent]): Result = Ok("Got request [" + request + "]")

  def showid(id: Int) = Action {
    val name = findNameById(id)
    if (findNameById(id) == "") {
      pageNotFound
      //oops
    } else {
      Ok("id="+id+",name="+name)
    }
  }

  def showstr(str: String) = Action { request =>
    val params = request.queryString.map(r => {
      val k = r._1
      val v = r._2.mkString
      k -> v
    })
    Ok("id="+str)
  }

  def page(pageId: Int) = Action {
    Ok("pageId="+pageId)
  }

  val test2 = Action {
    Result(
      header = ResponseHeader(200, Map(CONTENT_TYPE -> TEXT)),
      body = HttpEntity.Strict(ByteString("Hello world!"), Some("text/plain"))
    )
  }

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def redirect = Action {
    Redirect(routes.Application.index())
  }

  def findNameById(id: Int): String = {
    var ret = ""
    val arr = Array("cookeem","faith","fung","rui")
    if (id < arr.length) ret = arr(id)
    ret
  }

  //New session
  def session = Action {request =>
    Ok("Write session").withSession(request.session+("username" -> "cookeem"))
  }

  //Read session
  def session2 = Action { request =>
    request.session.get("username").map(username =>
      Ok(s"Read session username:$username")
    ).getOrElse(Ok("Read session error"))
  }

  //Clear session
  def session3 = Action {
    Ok("Clear Session").withNewSession
  }

  //curl -H "Content-Type: text/plain" -X POST  --data '{"data":"1"}' localhost:9000/bp1
  def bp1 = Action { request =>
    val body: AnyContent = request.body
    val textBody: Option[String] = body.asText

    // Expecting text body
    textBody.map { text =>
      Ok("Got: " + text)
    }.getOrElse {
      BadRequest("Expecting text/plain request body")
    }
  }

  //curl -H "Content-Type: application/json" -X POST  --data '{"data":"1"}' localhost:9000/bp2
  def bp2 = Action(parse.json) { request => {
      val text = request.body \\ "data"
      Ok("Got: " + text)
    }
  }

  //curl -d 'txt=hello' localhost:9000/bp3
  //接受任何文字
  def bp3 = Action(parse.tolerantText(maxLength = 10)) { request =>
    Ok("Got: " + request.body+ ", body length:"+request.body.length+"\n")
  }

  val storeInFile: BodyParser[File] = parse.using(request =>
      file(to = new File("/tmp/upload"))
  )

  //curl -F "blob=@zookeeper.out" localhost:9000/bp4
  //上传文件，并限制文件大小
  def bp4 = Action(parse.maxLength(2*1024,storeInFile)) { request =>
    request.body match {
      case Left(MaxSizeExceeded(length)) => BadRequest("Your file is too large, we accept just " + length + " bytes!")
      case Right(localFile) => {
        Ok("Saved the request content to " + localFile)
      }
    }
  }

  val storeInUserFile = parse.using {request =>
    request.cookies.get("username").map { user =>
      file(to = new File("/tmp/" + user + ".upload"))
    }.getOrElse {
      sys.error("You don't have the right to upload here")
    }
  }

  //curl -F "blob=@zookeeper.out" localhost:9000/bp5
  def bp5 = Action(parse.maxLength(10*1024,storeInUserFile)) { request =>
    request.body match {
      case Left(MaxSizeExceeded(length)) => BadRequest("Your file is too large, we accept just " + length + " bytes!")
      case Right(localFile) => {
        Ok("Saved the request content to " + localFile).withCookies(Cookie("username","cookeem"))
      }
    }
  }

  //curl -H "Content-Type: text/plain" -X POST  --data '{"data":"1"}' localhost:9000/tt1
  //curl -H "Content-Type: application/json" -X POST  --data '{"data":"1"}' localhost:9000/tt1
  val tt1 = Action {implicit request =>
    render {
      case Accepts.Html() => Ok("html")
      case Accepts.Json() => Ok("json")
      case Accepts.Xml() => Ok("xml")
    }
  }

  object LoggingAction extends ActionBuilder[Request] {
    def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]) = {
      val headers = request.headers.toSimpleMap.mkString(",\n")
      val requests = request.map(x => x.toString).queryString
      Logger.debug(s"method=${request.method} uri=${request.uri} remote-address=${request.remoteAddress}")
      Logger.info("{headers}:\n"+headers+"\n{requests}:\n"+requests+"\n{accepts}:"+Accepts.Html)
      block(request)
    }
  }

  def log1 = LoggingAction { request =>
    try {
      val headers = request.headers.toSimpleMap.mkString(",\n")
      val requests = request.map(x => x.toString).queryString
      Ok("{headers}:\n" + headers + "\n{requests}:\n" + requests + "\n{accepts}:\n" + Accepts.Html)
      sys.error("Haijian throw error!")
    } catch {
      case t: Throwable => {
        logger.error("Error in server: ", t)
        InternalServerError("Error in server: " + t.getMessage())
      }
    }
  }

  def logging[A](action: Action[A])= Action.async(action.parser) { request =>
    Logger.info("Calling action2")
    action(request)
  }

  def log2 = logging(Action {
    Ok("logs")
  })

  def log3 = Action { request =>
    Logger.info("Attempting risky calculation.")
    Ok("Attempting risky calculation.")
  }

  def toupload = Action {
    Ok(views.html.toupload())
  }

  def upload() = Action(parse.maxLength(1024* 1024, parse.multipartFormData)) { request =>
    request.body match {
      case Left(MaxSizeExceeded(length)) => BadRequest("Your file is too large, we accept just " + length + " bytes!")
      case Right(multipartForm) => {
        /* Handle the POSTed form with files */
        multipartForm.file("picture").map { picture =>
          val filename = picture.filename
          val contentType = picture.contentType
          picture.ref.moveTo(new File(s"/Users/cookeem/$filename"))
          Ok("File uploaded")
        }.getOrElse {
          Redirect(routes.Application.index).flashing(
            "error" -> "Missing file")
        }
      }
    }
  }

  def asyn1 = Action {
    Thread.sleep(10*1000)
    Ok("Wait for "+10+" seconds!")
  }

  val futureDouble: Future[Double] = scala.concurrent.Future {
    Thread.sleep(5*1000)
    3.14D
  }

  val futureResult: Future[Result] = futureDouble.map { pi =>
    Ok("PI value computed: " + pi)
  }

  def asyn2 = Action.async {
    futureDouble.map(d => {
      Logger.info("Got asyn result: " + d)
      Ok("Got result: " + d)
    })
  }

  //Streaming原始方式,出现异常
  def streaming1 = Action {
    try {
      val fileName = "/Volumes/Share/Book/AkkaScala2.3.14.pdf"
      val file = new File(fileName)
      val inputStream = new FileInputStream(fileName)
      //play2.5使用ByteString
      val fileContent: Source[ByteString, Future[IOResult]] = StreamConverters.fromInputStream(() => inputStream)
      val contentType = play.api.libs.MimeTypes.forFileName(fileName).orElse(Some(play.api.http.ContentTypes.BINARY))
      Result(
        header = ResponseHeader(200, Map(CONTENT_LENGTH -> file.length.toString)),
        //play2.5使用HttpEntity,并且需要声明ContentLength以及ContentType
        body = HttpEntity.Streamed(fileContent, Some(file.length()), contentType)
      )
    } catch {
      case t: Throwable =>
        InternalServerError("Error in server: " + t.getMessage())
    }
  }

  //出现异常
  def streaming2 = Action {
    Ok.sendFile(
      content = new java.io.File("/Volumes/Share/Book/AkkaScala2.3.14.pdf"),
      fileName = _ => "termsOfService.pdf", //保存时的本地文件名字
      inline = true
    )
  }

  //出现异常
  def streaming3 = Action {
    //文件大小未定的chunk response，只可以加载流式文件，例如文本或者jpg
    val file = new File("/Volumes/Share/Book/AkkaScala2.3.14.pdf")
    val in: FileInputStream = new FileInputStream(file)
    val dataContent: Enumerator[Array[Byte]] = Enumerator.fromStream(in, 1024*1024)
    //Play2.5 Enumerator建议转换为Source
    val source: Source[Array[Byte], NotUsed] = Source.fromPublisher(Streams.enumeratorToPublisher(dataContent))
    Ok.chunked(source).as("application/pdf")
  }

  def comet1 = Action {
    implicit val m = materializer
    def stringSource = Source(List(JsString("jsonString")))
    //comet内容输出到script中
    Ok.chunked(stringSource via Comet.json("parent.cometMessage")).as(JSON)
  }

  //websocket1 ####################################
  //持久化的websocket

  var childNum = 0

  class WebSocketActor1(out: ActorRef) extends Actor {
    override def preStart() = {
      println(s"$self connected!")
    }

    override def postStop() = {
      println(s"$self disconnected!")
      self ! PoisonPill
    }

    def receive = {
      case msg: String =>
        println(s"$out receive $msg")
        out ! (s"Server got: $msg")
    }
  }

  def socket1 = WebSocket.accept[String, String] { request =>
    //Play 2.5.0 使用ActorFlow接收request,然后把message发送到对应的actor
    ActorFlow.actorRef[String, String](out => Props(new WebSocketActor1(out)))
  }

  def websocket1 = Action {
    Ok(views.html.websocket1())
  }

  //websocket2 ####################################
  def socket2 = WebSocket.accept { request =>
    val in: Enumeratee[String, String] = Concurrent.buffer[String](10)
    Flow.fromProcessor(() => Streams.enumerateeToProcessor(in)).map(s => s"receive $s")
  }

  def websocket2 = Action {
    Ok(views.html.websocket2())
  }

  //websocket3 ####################################
  //记录websocket的channel并进行消息推送。不使用actor
  //Application变成class之后,无法记录sessions
  val sessions = ArrayBuffer[Channel[String]]()
  def socket3 = WebSocket.accept[String, String] { request =>
    val (in, channel) = Concurrent.broadcast[String]
    val sink = Sink.foreach[String]{msg =>
      println(msg)
      sessions.foreach(ch => ch.push(s"I received your message: $msg"))
    }
    val source = Source.fromPublisher(Streams.enumeratorToPublisher(in))
    sessions += channel
    Logger.info(channel.toString+", clients number:"+sessions.size)
    Flow.fromSinkAndSource(
      sink = sink,
      source = source
    )
  }

  def websocket3 = Action {
    Ok(views.html.websocket3())
  }

  def template1() = Action {
    val customer = Customer(name = "cookeem")
    val orders = List(
      Order("product1",2.3D),
      Order("product2",3.1D),
      Order("product3",5.0D)
    )
    val content = views.html.template1(customer, orders)
    Ok(content)
  }

  //jdbc ####################################
  def jdbc1 = Action {
    var outString = "Number is "
    db.database("test").withConnection { conn =>
      try {
        val stmt = conn.createStatement
        val rs = stmt.executeQuery("SELECT * FROM COFFEES")
        while (rs.next()) {
          for (i <- 1 to rs.getMetaData().getColumnCount) {
            val key = rs.getMetaData().getColumnName(i)
            val valueType = rs.getMetaData().getColumnClassName(i)
            val value = rs.getString(i)
            outString += s"($key -> $value -> $valueType)"
          }
          outString += "\n"
        }
      } catch {
        case e: Throwable => println(e.getMessage + ":" +e.getCause)
      }
    }
    Ok(outString)
  }

  //json1 ##################################################
  case class Location(lat: Double, long: Double)
  case class Resident(name: String, age: Int, role: Option[String])
  case class Place(name: String, location: Location, residents: List[Resident])

  def json1 = Action {
    val jsonValue: JsValue = Json.parse(
      """
        {
          "name" : "Watership Down",
          "location" : {
            "lat" : 51.235685,
            "long" : -1.309197
          },
          "residents" : [ {
            "name" : "Fiver",
            "age" : 4,
            "role" : null
          }, {
            "name" : "Bigwig",
            "age" : 6,
            "role" : "Owsla"
          } ]
        }
                                      """)
    //JsValue读取到class，class case 务必在外层，不能在函数体内
    implicit val locationReads = Json.reads[Location]
    implicit val residentReads = Json.reads[Resident]
    implicit val placeReads = Json.reads[Place]
    val Some(place) = jsonValue.validate[Place].asOpt
    println(place)

    //class转换成jsonString
    implicit val locationWrites = Json.writes[Location]
    implicit val residentWrites = Json.writes[Resident]
    implicit val placeWrites = Json.writes[Place]
    val jsonValue2: JsValue = Json.toJson(place)
    val jsonGood = Json.prettyPrint(jsonValue2)

    Ok(jsonGood).as(JSON)
  }

  //ws1 ##################################################
  def ws1() = Action.async {
    ws.url("http://localhost:9000/json1")
      .withHeaders("Accept" -> "application/json")
      .withFollowRedirects(true)
      .withRequestTimeout(10.seconds)
      .get.map(response => {
      val html = "###What I get: \n"+response.body
      Ok(html)
    })
  }

  //ws2 ##################################################
  //通过webservice直接显示文件
  def ws2() = Action.async {
    ws.url("http://www.iteblog.com/pic/weixin.jpg").withMethod("GET").stream().map {
      case StreamedResponse(response, body) =>
        // Check that the response was successful
        if (response.status == 200) {
          // Get the content type
          val contentType = response.headers.get("Content-Type").flatMap(_.headOption)
            .getOrElse("application/octet-stream")
          // If there's a content length, send that, otherwise return the body chunkedx
          response.headers.get("Content-Length") match {
            case Some(Seq(length)) =>
              Ok.sendEntity(HttpEntity.Streamed(body, Some(length.toLong), Some(contentType)))
            case _ =>
              Ok.chunked(body).as(contentType)
          }
        } else {
          BadGateway
        }
    }
  }

  //ws3 ##################################################
  //通过webservice下载文件
  def ws3() = Action.async {
    val file = new File("/Users/cookeem/weixin.jpg")
    val fileStream = ws.url("http://www.iteblog.com/pic/weixin.jpg").withMethod("GET").stream()
    fileStream.flatMap {
      res =>
        val outputStream = new FileOutputStream(file)
        // The sink that writes to the output stream
        val sink = Sink.foreach[ByteString] { bytes =>
          outputStream.write(bytes.toArray)
        }
        // materialize and run the stream
        res.body.runWith(sink).andThen {
          case result =>
            // Close the output stream whether there was an error or not
            outputStream.close()
            // Get the result or rethrow the error
            result.get
        }.map(_ => file)
    }
    Future{Ok("Download Ok")}
  }
}

