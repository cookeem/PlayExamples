package actor

import akka.actor._
import akka.serialization.SerializationExtension

/**
 * Created by cookeem on 15/12/31.
 */
case class SerializeMsg(content: String, fromuid: Int, touid: Int, dateline: Long)

class SerializeActor extends Actor {
  def receive = {
    case s: String =>
      println(s"$self receive $s")
  }
}

object SerializerTest extends App {
  val system = ActorSystem("system")
  val serialization = SerializationExtension(system)

  val smsg = SerializeMsg("my content", 1, 2, 3L)
  println(s"smsg: $smsg")
  // Find the Serializer for it
  val serializer = serialization.findSerializerFor(smsg)
  // Turn it into bytes
  val bytes = serializer.toBinary(smsg)
  println(s"bytes: $bytes")
  val back = serializer.fromBinary(bytes, manifest = None).asInstanceOf[SerializeMsg]
  println(s"back: $back")


  system.terminate()
}
