package actor

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.softwaremill.session._
import com.softwaremill.session.CsrfDirectives._
import com.softwaremill.session.CsrfOptions._
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._

import scala.util.{Random, Try}

/**
  * Created by cookeem on 16/7/10.
  */
object HttpSessionTest1 extends App {
  implicit val system = ActorSystem("example")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val token = Random.alphanumeric.take(64).toSeq.mkString
  val sessionConfig = SessionConfig.default(token)
  implicit val sessionManager = new SessionManager[ExampleSession](sessionConfig)

  implicit val refreshTokenStorage = new InMemoryRefreshTokenStorage[ExampleSession] {
    def log(msg: String) = println(s"refreshTokenStorage msg: $msg")
  }

  val routes =
    path("") {
      complete("hello world")
    } ~
      randomTokenCsrfProtection(checkHeader) {
        pathPrefix("api") {
          path("login" / Segment) { loginname =>
            println(s"Logging in $loginname")
            setSession(refreshable, usingCookies, ExampleSession(loginname)) {
              setNewCsrfToken(checkHeader) { ctx => ctx.complete(s"Logging in $loginname") }
            }
          } ~
            // This should be protected and accessible only when logged in
            path("logout") {
              requiredSession(refreshable, usingCookies) { session =>
                invalidateSession(refreshable, usingCookies) { ctx =>
                  println(s"Logging out $session")
                  ctx.complete(s"Logging out $session")
                }
              }
            } ~
            // This should be protected and accessible only when logged in
            path("current") {
              requiredSession(refreshable, usingCookies) { session => ctx =>
                println("Current session: " + session)
                ctx.complete("Current session: " + session)
              }
            } ~
            // This would not throw 403 error
            path("current2") {
              optionalSession(refreshable, usingCookies) { session => ctx =>
                println("Current session2: " + session)
                ctx.complete("Current session2: " + session)
              }
            }
        }
      }

  Http().bindAndHandle(routes, "localhost", 8080)
}

case class ExampleSession(username: String)

object ExampleSession {
  implicit def serializer: SessionSerializer[ExampleSession, String] = new SingleValueSessionSerializer(
    _.username,
    (un: String) => Try { ExampleSession(un) }
  )
}