package actor

import akka.actor._
import akka.event.Logging
import akka.serialization.Serializer
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.util.{ListReferenceResolver, MapReferenceResolver}
import com.romix.akka.serialization.kryo.{ObjectPool, KryoBasedSerializer, KryoSerialization}
import com.romix.scala.serialization.kryo.KryoClassResolver
import com.typesafe.config.ConfigFactory
import org.objenesis.strategy.StdInstantiatorStrategy
import com.esotericsoftware.minlog.{Log => MiniLog}

import scala.collection.JavaConversions._
import scala.util.{Failure, Success}

/**
 * Created by cookeem on 16/1/1.
 */
case class MsgKryoTest(content: String)
case class MsgKryoTest2(fromuid: Int, touid: Int, dateline: Long, content: String)

class KryoActor extends Actor {
  val remote = context.actorOf(Props[KryoRemoteActor],"remote")
  def receive = {
    case msg: MsgKryoTest =>
      println(s"$self receive $msg")
      remote ! msg
    case msg: MsgKryoTest2 =>
      println(s"$self receive $msg")
      remote ! msg
    case _ =>
      println(s"$self receive unknown msg!")
  }
}

class KryoRemoteActor extends Actor {
  def receive = {
    case msg: MsgKryoTest =>
      println(s"$self receive $msg")
    case msg: MsgKryoTest2 =>
      println(s"$self receive $msg")
    case _ =>
      println(s"$self receive unknown msg!")
  }
}

class ExtendedKryoSerializer(val system: ExtendedActorSystem) extends Serializer {

  /**
   * This class help us to serialize class
   * and to add serializers as kryo default serializer.
   */
  import KryoSerialization._
  val log = Logging(system, getClass.getName)
  val settings = new Settings(system.settings.config)
  val mappings = settings.ClassNameMappings
  locally {
    log.debug("Got mappings: {}", mappings)
  }
  val classnames = settings.ClassNames
  locally {
    log.debug("Got classnames for incremental strategy: {}", classnames)
  }
  val bufferSize = settings.BufferSize
  locally {
    log.debug("Got buffer-size: {}", bufferSize)
  }
  val maxBufferSize = settings.MaxBufferSize
  locally {
    log.debug("Got max-buffer-size: {}", maxBufferSize)
  }
  val serializerPoolSize = settings.SerializerPoolSize
  val idStrategy = settings.IdStrategy
  locally {
    log.debug("Got id strategy: {}", idStrategy)
  }
  val serializerType = settings.SerializerType
  locally {
    log.debug("Got serializer type: {}", serializerType)
  }
  val implicitRegistrationLogging = settings.ImplicitRegistrationLogging
  locally {
    log.debug("Got implicit registration logging: {}", implicitRegistrationLogging)
  }
  val useManifests = settings.UseManifests
  locally {
    log.debug("Got use manifests: {}", useManifests)
  }
  val serializer = try new KryoBasedSerializer(
    getKryo(idStrategy, serializerType),
    bufferSize,
    maxBufferSize,
    serializerPoolSize,
    useManifests)
  catch {
    case e: Exception => {
      log.error("exception caught during akka-kryo-serialization startup: {}", e)
      throw e
    }
  }

  locally {
    log.debug("Got serializer: {}", serializer)
  }

  // This is whether "fromBinary" requires a "clazz" or not
  def includeManifest: Boolean = useManifests

  // A unique identifier for this Serializer
  def identifier = 123454323

  def toBinary(obj: AnyRef): Array[Byte] = {
    val ser = getSerializer
    val bin = ser.toBinary(obj)
    releaseSerializer(ser)
    bin
  }

  def fromBinary(bytes: Array[Byte], clazz: Option[Class[_]]): AnyRef = {
    val ser = getSerializer
    val obj = ser.fromBinary(bytes, clazz)
    releaseSerializer(ser)
    obj
  }

  val serializerPool = new ObjectPool[Serializer](serializerPoolSize, () => {
    new KryoBasedSerializer(
      getKryo(idStrategy, serializerType),
      bufferSize,
      maxBufferSize,
      serializerPoolSize,
      useManifests = useManifests)
  })

  private def getSerializer = serializerPool.fetch
  private def releaseSerializer(ser: Serializer) = serializerPool.release(ser)

  private def getKryo(strategy: String, serializerType: String): Kryo = {
    val referenceResolver = if (settings.KryoReferenceMap) new MapReferenceResolver() else new ListReferenceResolver()
    val kryo = new Kryo(new KryoClassResolver(implicitRegistrationLogging), referenceResolver)
    // Support deserialization of classes without no-arg constructors
    kryo.setInstantiatorStrategy(new StdInstantiatorStrategy())

    if (settings.KryoTrace)
      MiniLog.TRACE()

    strategy match {
      case "default" => {}

      case "incremental" => {
        kryo.setRegistrationRequired(false)

        for ((fqcn: String, idNum: String) <- mappings) {
          val id = idNum.toInt
          // Load class
          system.dynamicAccess.getClassFor[AnyRef](fqcn) match {
            case Success(clazz) => kryo.register(clazz, id)
            case Failure(e) => {
              log.error("Class could not be loaded and/or registered: {} ", fqcn)
              throw e
            }
          }
        }
        for (classname <- classnames) {
          // Load class
          system.dynamicAccess.getClassFor[AnyRef](classname) match {
            case Success(clazz) => kryo.register(clazz)
            case Failure(e) => {
              log.warning("Class could not be loaded and/or registered: {} ", classname)
              throw e
            }
          }
        }
      }

      case "explicit" => {
        kryo.setRegistrationRequired(false)

        for ((fqcn: String, idNum: String) <- mappings) {
          val id = idNum.toInt
          // Load class
          system.dynamicAccess.getClassFor[AnyRef](fqcn) match {
            case Success(clazz) => kryo.register(clazz, id)
            case Failure(e) => {
              log.error("Class could not be loaded and/or registered: {} ", fqcn)
              throw e
            }
          }
        }

        for (classname <- classnames) {
          // Load class
          system.dynamicAccess.getClassFor[AnyRef](classname) match {
            case Success(clazz) => kryo.register(clazz)
            case Failure(e) => {
              log.warning("Class could not be loaded and/or registered: {} ", classname)
              throw e
            }
          }
        }
        kryo.setRegistrationRequired(true)
      }
    }

    serializerType match {
      case "graph" => kryo.setReferences(true)
      case _ => kryo.setReferences(false)
    }

    kryo
  }
}

object KryoTest extends App {
  val configStr = """
    akka {
      extensions = ["com.romix.akka.serialization.kryo.KryoSerializationExtension$"]
      actor {
        provider = "akka.remote.RemoteActorRefProvider"
        deployment {
          #用于远程创建actor的配置
          #注意,此actor不能由system创建
          /remote {
            remote = "akka.tcp://system@localhost:2553"
          }
        }
        remote {
          netty {
            hostname = "localhost"
            port = 2552
          }
        }
        kryo  {
          type = "graph"
          idstrategy = "incremental"
          serializer-pool-size = 16
          buffer-size = 4096
          use-manifests = false
          implicit-registration-logging = true
          kryo-trace = false
          classes =[
          "actor.MsgKryoTest"
          "actor.MsgKryoTest2"
          "scala.Some"
          "scala.None$"
          ]
        }
        serializers {
          java = "akka.serialization.JavaSerializer"
          kryo = "com.romix.akka.serialization.kryo.KryoSerializer"
        }
        serialization-bindings {
          "actor.MsgKryoTest"=kryo
          "actor.MsgKryoTest2"=kryo
          "scala.Some"=kryo
          "scala.None$"=kryo
        }
      }
    }"""
  val config = ConfigFactory.parseString(configStr)
  val system = ActorSystem("system",config)
  val ref = system.actorOf(Props[KryoActor],"kryo")
  ref ! MsgKryoTest("this is MsgKryoTest content")
  ref ! MsgKryoTest2(101,102,System.currentTimeMillis(),"this is MsgKryoTest2 content")

}
