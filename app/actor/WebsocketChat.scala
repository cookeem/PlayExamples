package actor

/**
  * Created by cookeem on 16/6/15.
  */
import akka.actor._
import akka.http.scaladsl.server.PathMatchers
import akka.stream._
import akka.stream.scaladsl._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.server.Directives._
import scala.concurrent.duration._

/**
  * Created by cookeem on 16/6/13.
  */

object WebsocketChat extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  val echoService: Flow[Message, TextMessage, Any] = Flow[Message].collect {
    case TextMessage.Strict(txt) =>
      TextMessage("ECHO: " + txt)
    case bm: BinaryMessage =>
      TextMessage("Unacceptable message type")
    // ignore binary messages
  }.keepAlive(45.seconds, () => TextMessage("keepalive"))
  //websocket默认会在60秒后超时,自动发送keepAlive消息,可以配置akka.http.server.idle-timeout超时时间

  val route = get {
    path("ws-echo") {
      handleWebSocketMessages(echoService)
    } ~
      pathPrefix("ws-chat" / IntNumber) { chatId =>
        path(PathMatchers.Segment) { username =>
          handleWebSocketMessages(ChatRooms.findOrCreate(chatId).chatService(username))
        }
      }
  } ~
    pathSingleSlash {
      get {
        getFromFile("www/websocket.html")
      }
    }

  val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
}

class ChatRoom(roomId: Int, actorSystem: ActorSystem) {
  private[this] val chatRoomActor = actorSystem.actorOf(Props(classOf[ChatRoomActor], roomId))
  println(s"create new chatRoomActor: $chatRoomActor")

  val source = Source.actorRef[ChatEvent](bufferSize = 5, OverflowStrategy.fail)

  def chatService(user: String) = Flow.fromGraph(GraphDSL.create(source) { implicit builder =>
    chatSource => //source provideed as argument
      import GraphDSL.Implicits._
      //flow used as input it takes Message's
      val fromWebsocket = builder.add(
        Flow[Message].collect {
          case TextMessage.Strict(txt) => ChatMessage(user, txt)
        }
      )
      //flow used as output, it returns Message's
      val backToWebsocket = builder.add(
        Flow[ChatEvent].collect {
          case ChatMessage(author, text) => TextMessage(s"[$author]: $text")
        }
      )
      //send messages to the actor, if send also UserLeft(user) before stream completes.
      val chatActorSink = Sink.actorRef[ChatEvent](chatRoomActor, UserLeft(user))
      //merges both pipes
      val merge = builder.add(Merge[ChatEvent](2))
      //Materialized value of Actor who sit in chatroom, 获取来源的stream actor
      val actorAsSource = builder.materializedValue.map(actor => UserJoined(user, actor))
      //Message from websocket is converted into IncommingMessage and should be send to each in room
      fromWebsocket ~> merge.in(0)
      //If Source actor is just created should be send as UserJoined and registered as particiant in room
      actorAsSource ~> merge.in(1)
      //Merges both pipes above and forward messages to chatroom Represented by ChatRoomActor
      merge ~> chatActorSink
      //Actor already sit in chatRoom so each message from room is used as source and pushed back into websocket
      chatSource ~> backToWebsocket
      // expose ports
      FlowShape(fromWebsocket.in, backToWebsocket.out)
  }).keepAlive(45.seconds, () => TextMessage("keepalive"))
}

object ChatRoom {
  def apply(roomId: Int)(implicit actorSystem: ActorSystem) = new ChatRoom(roomId, actorSystem)
}

sealed trait ChatEvent
case class UserJoined(name: String, userActor: ActorRef) extends ChatEvent
case class UserLeft(name: String) extends ChatEvent
case class ChatMessage(sender: String, message: String) extends ChatEvent

object SystemMessage {
  def apply(text: String) = ChatMessage("System", text)
}

class ChatRoomActor(roomId: Int) extends Actor {
  var participants: Map[String, ActorRef] = Map.empty[String, ActorRef]

  override def receive: Receive = {
    case UserJoined(name, actorRef) =>
      participants += name -> actorRef
      broadcast(SystemMessage(s"User $name joined channel..."))
      println(s"User $name joined channel[$roomId], from stream actor: $actorRef")
    case UserLeft(name) =>
      println(s"User $name left channel[$roomId]")
      broadcast(SystemMessage(s"User $name left channel[$roomId]"))
      participants -= name
    case msg: ChatMessage =>
      broadcast(msg)
  }

  def broadcast(message: ChatEvent): Unit = participants.values.foreach(_ ! message)
}

object ChatRooms {
  var chatRooms: Map[Int, ChatRoom] = Map.empty[Int, ChatRoom]
  def findOrCreate(number: Int)(implicit actorSystem: ActorSystem): ChatRoom = chatRooms.getOrElse(number, createNewChatRoom(number))
  private def createNewChatRoom(number: Int)(implicit actorSystem: ActorSystem): ChatRoom = {
    val chatroom = ChatRoom(number)
    chatRooms += number -> chatroom
    chatroom
  }
}
