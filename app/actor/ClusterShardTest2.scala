package actor

import java.util.UUID

import akka.actor._
import akka.pattern.ask
import akka.cluster.Cluster
import akka.cluster.sharding.{ClusterShardingSettings, ClusterSharding, ShardRegion}
import akka.cluster.sharding.ShardRegion.Passivate
import akka.persistence.PersistentActor
import akka.persistence.journal.leveldb.{SharedLeveldbJournal, SharedLeveldbStore}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.collection.immutable
import scala.concurrent.duration._

import ClusterShardObj2._
/**
 * Created by cookeem on 16/1/8.
 */
object ClusterShardObj2 {
  case class PostSummary(author: String, postId: String, title: String)
  case class GetPosts(author: String)
  case class Posts(list: immutable.IndexedSeq[PostSummary])

  object PostContent {
    val empty = PostContent("", "", "")
  }
  case class PostContent(author: String, title: String, body: String)

  sealed trait Command {
    def postId: String
  }
  case class AddPost(postId: String, content: PostContent) extends Command
  case class GetContent(postId: String) extends Command
  case class ChangeBody(postId: String, body: String) extends Command
  case class Publish(postId: String) extends Command

  sealed trait Event
  case class PostAdded(content: PostContent) extends Event
  case class BodyChanged(body: String) extends Event
  case object PostPublished extends Event

  case object Tick

  val configStr = """
    #akka.actor.warn-about-java-serializer-usage = off
    akka {
      loglevel = WARNING
      actor {
        provider = "akka.cluster.ClusterActorRefProvider"
      }
      remote {
        log-remote-lifecycle-events = off
        netty.tcp {
          hostname = "localhost"
          port = 0
        }
      }
      cluster {
        seed-nodes = [
        "akka.tcp://ClusterSystem@localhost:2551"]
        auto-down-unreachable-after = 10s
        metrics.enabled = off
      }
      persistence {
        journal {
          plugin = "akka.persistence.journal.leveldb-shared"
          leveldb-shared.store {
            # DO NOT USE 'native = off' IN PRODUCTION !!!
            native = off
            dir = "target/shared-journal"
          }
        }
        snapshot-store {
          plugin = "akka.persistence.snapshot-store.local"
          local.dir = "target/snapshots"
        }
        snapshot-store.plugin = "akka.persistence.snapshot-store.local"
        snapshot-store.local.dir = "target/snapshots"
      }
    }"""

}

object AuthorListing {
  def props(): Props = Props(new AuthorListing)
  val idExtractor: ShardRegion.ExtractEntityId = {
    case s: PostSummary => (s.author, s)
    case m: GetPosts    => (m.author, m)
  }
  val shardResolver: ShardRegion.ExtractShardId = msg => msg match {
    case s: PostSummary   => (math.abs(s.author.hashCode) % 100).toString
    case GetPosts(author) => (math.abs(author.hashCode) % 100).toString
  }
  val shardName: String = "AuthorListing"
}

class AuthorListing extends PersistentActor with ActorLogging {
  override def persistenceId: String = self.path.parent.name + "-" + self.path.name
  // passivate the entity when no activity
  context.setReceiveTimeout(2.minutes)
  var posts = Vector.empty[PostSummary]
  def receiveCommand = {
    case s: PostSummary =>
      persist(s) { evt =>
        posts :+= evt
        log.info("Post added to {}'s list: {}", s.author, s.title)
      }
    case GetPosts(_) =>
      sender() ! Posts(posts)
    case ReceiveTimeout => context.parent ! Passivate(stopMessage = PoisonPill)
  }
  override def receiveRecover: Receive = {
    case evt: PostSummary => posts :+= evt
  }
}

object Post {

  def props(authorListing: ActorRef): Props =
    Props(new Post(authorListing))

  val idExtractor: ShardRegion.ExtractEntityId = {
    case cmd: Command => (cmd.postId, cmd)
  }

  val shardResolver: ShardRegion.ExtractShardId = {
    case cmd: Command => (math.abs(cmd.postId.hashCode) % 100).toString
  }

  val shardName: String = "Post"

  private case class State(content: PostContent, published: Boolean) {

    def updated(evt: Event): State = evt match {
      case PostAdded(c)   => copy(content = c)
      case BodyChanged(b) => copy(content = content.copy(body = b))
      case PostPublished  => copy(published = true)
    }
  }
}

class Post(authorListing: ActorRef) extends PersistentActor with ActorLogging {

  import Post._

  // self.path.parent.name is the type name (utf-8 URL-encoded)
  // self.path.name is the entry identifier (utf-8 URL-encoded)
  override def persistenceId: String = self.path.parent.name + "-" + self.path.name

  // passivate the entity when no activity
  context.setReceiveTimeout(2.minutes)

  private var state = State(PostContent.empty, false)

  override def receiveRecover: Receive = {
    case evt: PostAdded =>
      context.become(created)
      state = state.updated(evt)
    case evt @ PostPublished =>
      context.become(published)
      state = state.updated(evt)
    case evt: Event => state =
      state.updated(evt)
  }

  override def receiveCommand: Receive = initial

  def initial: Receive = {
    case GetContent(_) => sender() ! state.content
    case AddPost(_, content) =>
      if (content.author != "" && content.title != "")
        persist(PostAdded(content)) { evt =>
          state = state.updated(evt)
          context.become(created)
          log.info("New post saved: {}", state.content.title)
        }
  }

  def created: Receive = {
    case GetContent(_) => sender() ! state.content
    case ChangeBody(_, body) =>
      persist(BodyChanged(body)) { evt =>
        state = state.updated(evt)
        log.info("Post changed: {}", state.content.title)
      }
    case Publish(postId) =>
      persist(PostPublished) { evt =>
        state = state.updated(evt)
        context.become(published)
        val c = state.content
        log.info("Post published: {}", c.title)
        authorListing ! PostSummary(c.author, postId, c.title)
      }
  }

  def published: Receive = {
    case GetContent(_) => sender() ! state.content
  }

  override def unhandled(msg: Any): Unit = msg match {
    case ReceiveTimeout => context.parent ! Passivate(stopMessage = PoisonPill)
    case _              => super.unhandled(msg)
  }

}

class Bot extends Actor with ActorLogging {
  import context.dispatcher
  val tickTask = context.system.scheduler.schedule(3.seconds, 3.seconds, self, Tick)

  val postRegion = ClusterSharding(context.system).shardRegion(Post.shardName)
  val listingsRegion = ClusterSharding(context.system).shardRegion(AuthorListing.shardName)

  val from = Cluster(context.system).selfAddress.hostPort

  override def postStop(): Unit = {
    super.postStop()
    tickTask.cancel()
  }

  var n = 0
  val authors = Map(0 -> "Patrik", 1 -> "Martin", 2 -> "Roland", 3 -> "Björn", 4 -> "Endre")
  def currentAuthor = authors(n % authors.size)

  def receive = create

  val create: Receive = {
    case Tick =>
      val postId = UUID.randomUUID().toString
      n += 1
      val title = s"Post $n from $from"
      postRegion ! AddPost(postId, PostContent(currentAuthor, title, "..."))
      context.become(edit(postId))
  }

  def edit(postId: String): Receive = {
    case Tick =>
      postRegion ! ChangeBody(postId, "Something very interesting ...")
      context.become(publish(postId))
  }

  def publish(postId: String): Receive = {
    case Tick =>
      postRegion ! Publish(postId)
      context.become(list)
  }

  val list: Receive = {
    case Tick =>
      listingsRegion ! GetPosts(currentAuthor)
    case Posts(summaries) =>
      log.info("Posts by {}: {}", currentAuthor, summaries.map(_.title).mkString("\n\t", "\n\t", ""))
      context.become(create)
  }

}

object ClusterShardTest2 extends App {
  if (args.isEmpty)
    startup(Seq("2551", "2552", "0"))
  else
    startup(args)

  def startup(ports: Seq[String]): Unit = {
    ports foreach { port =>
      // Override the configuration of the port
      val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port").
        withFallback(ConfigFactory.parseString(configStr))
      println(s"config: $config")

      // Create an Akka system
      val system = ActorSystem("ClusterSystem", config)

      startupSharedJournal(system, startStore = (port == "2551"), path =
        ActorPath.fromString("akka.tcp://ClusterSystem@localhost:2551/user/store"))

      val authorListingRegion = ClusterSharding(system).start(
        typeName = AuthorListing.shardName,
        entityProps = AuthorListing.props(),
        settings = ClusterShardingSettings(system),
        extractEntityId = AuthorListing.idExtractor,
        extractShardId = AuthorListing.shardResolver)
      ClusterSharding(system).start(
        typeName = Post.shardName,
        entityProps = Post.props(authorListingRegion),
        settings = ClusterShardingSettings(system),
        extractEntityId = Post.idExtractor,
        extractShardId = Post.shardResolver)

      if (port == "0")
        system.actorOf(Props[Bot], "bot")
    }

    def startupSharedJournal(system: ActorSystem, startStore: Boolean, path: ActorPath): Unit = {
      // Start the shared journal one one node (don't crash this SPOF)
      // This will not be needed with a distributed journal
      if (startStore)
        system.actorOf(Props[SharedLeveldbStore], "store")
      // register the shared journal
      import system.dispatcher
      implicit val timeout = Timeout(15.seconds)
      val f = (system.actorSelection(path) ? Identify(None))
      f.onSuccess {
        case ActorIdentity(_, Some(ref)) => SharedLeveldbJournal.setStore(ref, system)
        case _ =>
          system.log.error("Shared journal not started at {}", path)
          system.terminate()
      }
      f.onFailure {
        case _ =>
          system.log.error("Lookup of shared journal at {} timed out", path)
          system.terminate()
      }
    }
  }
}
