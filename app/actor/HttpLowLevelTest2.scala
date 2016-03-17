package actor

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer

/**
 * Created by cookeem on 16/2/18.
 */
object HttpLowLevelTest2 extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
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
  Http().bindAndHandleSync(requestHandler, "localhost", 8080)
}
