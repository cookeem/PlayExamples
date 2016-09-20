package actor

import java.nio.file.Paths

import akka.Done
import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.stream.{IOResult, ActorMaterializer}
import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.FileIO

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
 * Created by cookeem on 16/2/18.
 */
object HttpHighLevelTest1 extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  case class Person(name: String, age: Int)

  val route =
    get {
      pathSingleSlash {
        complete {
          <html>
            <body>
              Hello world!
              <form action="upload" method="post" enctype="multipart/form-data">
                <input type="file" name="file" accept="image/*" />
                <input type="submit" />
              </form>
            </body>
          </html>
        }
      } ~
      path("ping") {
        complete("PONG!")
      } ~
      path("crash") {
        sys.error("BOOM!")
      } ~
      path("json") {
        complete{
          HttpEntity(ContentTypes.`application/json`, """{"key":"value"}""")
        }
      }
    } ~
    path("upload") {
      post {
        fileUpload("file") {
          case (fileInfo, fileStream) =>
            val sink = FileIO.toPath(Paths.get(fileInfo.fileName))
            val writeResult: Future[IOResult] = fileStream.runWith(sink)
            onSuccess(writeResult) { result =>
              result.status match {
                case Success(Done) =>
                  complete(s"Successfully written ${result.count} bytes")
                case Failure(e) =>
                  complete(s"Fail upload! ${e.getClass}, ${e.getMessage}, ${e.getCause}, ${e.getStackTrace.mkString("\n")}")
              }
            }
        }
      }
    }

  Http().bindAndHandle(route, "localhost", 8080)
}
