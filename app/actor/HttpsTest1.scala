package actor

import java.io.FileInputStream
import java.security.{SecureRandom, KeyStore}
import javax.net.ssl.{KeyManagerFactory, SSLContext}

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.{TextMessage, Message}
import akka.http.scaladsl.{ConnectionContext, Http}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow

/**
  * Created by cookeem on 16/6/28.
  */
object HttpsTest1 extends App {
  implicit val system = ActorSystem("server")
  implicit val materializer = ActorMaterializer()
  import system._

  /*
  * It must create self-signed cert p12 file: server.p12
  * How to generate self signed p12 file:
  * Generate private key: openssl genrsa 2048 > private.pem
  * Generate the self signed certificate: openssl req -x509 -new -key private.pem -out public.pem
  * create P12 file and input password: openssl pkcs12 -export -in public.pem -inkey private.pem -out server.p12
  */
  /*
  * Create p12 file from cert file and key file: openssl pkcs12 -export -out cookeem.com.pfx -inkey 4_user_cookeem.com.key -in 3_user_cookeem.com.crt
  */
  val serverContext: ConnectionContext = {
    val password = "asdasd".toCharArray
    val context = SSLContext.getInstance("TLS")
    val ks = KeyStore.getInstance("PKCS12")
    ks.load(new FileInputStream("https/keys/cookeem.com.pfx"), password)
    val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(ks, password)
    context.init(keyManagerFactory.getKeyManagers, null, new SecureRandom)
    ConnectionContext.https(context)
  }

  val echoService: Flow[Message, Message, _] = Flow[Message].map {
    case TextMessage.Strict(txt) => TextMessage("ECHO: " + txt)
    case _ => TextMessage("Message type unsupported")
  }

  val route: Route = Route(
    path("ws") {
      getFromFile("https/web/index.html")
    } ~
    path("ws-echo") {
      get {
        handleWebSocketMessages(echoService)
      }
    } ~
    complete("ok"))

  Http().bindAndHandle(route, interface = "0.0.0.0", port = 8081, connectionContext = serverContext)
}
