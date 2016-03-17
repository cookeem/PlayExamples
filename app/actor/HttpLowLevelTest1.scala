package actor

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.HttpMethods._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink

/**
 * Created by cookeem on 16/2/18.
 */
object HttpLowLevelTest1 extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  val serverSource = Http().bind(interface = "localhost", port = 8080)
  val requestHandler: HttpRequest => HttpResponse = {
    case HttpRequest(GET, Uri.Path("/"), _, _, _) =>
      HttpResponse(entity = HttpEntity(ContentTypes.`text/html(UTF-8)`,
    "<html><body>Hello world!</body></html>"))
    case HttpRequest(GET, Uri.Path("/ping"), _, _, _) =>
      HttpResponse(entity = "PONG!")
    case HttpRequest(GET, Uri.Path("/crash"), _, _, _) =>
      sys.error("BOOM!")
    case _: HttpRequest =>
      HttpResponse(404, entity = "Unknown resource!")
  }
  val bindingFuture =
    serverSource.to(Sink.foreach { connection =>
      println("Accepted new connection from " + connection.remoteAddress)
      connection.handleWithSyncHandler(requestHandler)
      // this is equivalent to
      // connection handleWith { Flow[HttpRequest] map requestHandler }
    }).run()
}
