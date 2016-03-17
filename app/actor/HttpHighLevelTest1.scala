package actor

import akka.actor._
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._
import akka.http.scaladsl.server.Directives._

/**
 * Created by cookeem on 16/2/18.
 */
object HttpHighLevelTest1 extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  val route =
    get {
      pathSingleSlash {
        complete {
          <html>
            <body>Hello world!</body>
          </html>
        }
      } ~
        path("ping") {
          complete("PONG!")
        } ~
        path("crash") {
          sys.error("BOOM!")
        }
    }
  // ‘route‘ will be implicitly converted to ‘Flow‘ using ‘RouteResult.route2HandlerFlow‘
  Http().bindAndHandle(route, "localhost", 8080)
}
