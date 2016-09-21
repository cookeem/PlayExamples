package actor

/**
  * Created by cookeem on 16/6/15.
  */

import java.io.{File, FileOutputStream}

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
    case BinaryMessage.Strict(bs) =>
      //用于保存文件
      val filename = "test.data"
      val out = new FileOutputStream(new File(filename))
      out.write(bs.toByteBuffer.array())
      out.close()
      TextMessage(s"file size is: ${bs.length}")
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

  val source = Source.actorRef[ChatEvent](bufferSize = Int.MaxValue, OverflowStrategy.fail)


  /* FlowShape
  {{
         +------------------------------------------------------------+
  In  ~> | ~> fromWebsocket ~> +-------------+                        |
         |                     |chatActorSink| ....                   |
         |    actorAsSource ~> +-------------+    . chatRoomActor     |
         |                                        . to broadcast      |
  Out ~> | <~ backToWebsocket <~ chatSource <......                   |
         +------------------------------------------------------------+
  }}
  */
  def chatService(user: String) = Flow.fromGraph(GraphDSL.create(source) { implicit builder =>
    chatSource => //把source作为参数传入Graph
      import GraphDSL.Implicits._
      //从websocket接收Message，并转化成ChatMessage
      val fromWebsocket = builder.add(
        Flow[Message].collect {
          case TextMessage.Strict(txt) => ChatMessage(user, txt)
          case BinaryMessage.Strict(bs) =>
            //用于保存文件
            val filename = "test.data"
            val out = new FileOutputStream(new File(filename))
            out.write(bs.toByteBuffer.array())
            out.close()
            ChatMessage(user, s"send file size is: ${bs.length}")
        }
      )
      //把ChatMessage转化成Message，并输出到websocket
      val backToWebsocket = builder.add(
        Flow[ChatEvent].collect {
          case ChatMessage(author, text) => TextMessage(s"[$author]: $text")
        }
      )
      //把消息发送到chatRoomActor，chatRoomActor收到消息后会进行广播, 假如流结束的时候，向chatRoomActor发送UserLeft消息
      val chatActorSink = Sink.actorRef[ChatEvent](chatRoomActor, UserLeft(user))
      //聚合管道
      val merge = builder.add(Merge[ChatEvent](2))
      //进行流的物料化，当有新的流创建的时候，向该流中发送UserJoined(user, actor)消息
      val actorAsSource = builder.materializedValue.map(actor => UserJoined(user, actor))
      //聚合websocket的消息来源
      fromWebsocket ~> merge.in(0)
      //当有新的流创建的是否，发送UserJoined(user, actor)消息到聚合merge
      actorAsSource ~> merge.in(1)
      //把聚合merge消息发送到chatRoomActor，注意chatActorSink会广播消息到各个chatroom的chatSource
      merge ~> chatActorSink
      //chatSource收到广播消息之后，把消息发送给backToWebsocket
      chatSource ~> backToWebsocket
      //暴露端口：fromWebsocket.in, backToWebsocket.out
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
