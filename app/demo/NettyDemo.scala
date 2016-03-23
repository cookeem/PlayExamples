package demo

import play.api.mvc._
import play.core.server.{ServerConfig, NettyServer}
import play.api.routing.sird._

/**
  * Created by cookeem on 16/3/22.
  */
object NettyDemo extends App {
  val serverConfig = ServerConfig(
    port = Some(19000)
  )
  val server = NettyServer.fromRouter(serverConfig) {
    case GET(p"/posts/") => Action {
      Results.Ok("All posts")
    }
    case GET(p"/posts/$id") => Action {
      Results.Ok("Post:" + id )
    }
  }
}
