package org.byern.s33pakka

import java.util.UUID

import akka.actor._
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.persistence.journal.leveldb.SharedLeveldbStore
import akka.testkit._
import org.byern.s33pakka.config.{ShardMessageConfiguration, SharedStoreUsage}
import org.byern.s33pakka.player.Player
import org.byern.s33pakka.session.SessionManager
import org.byern.s33pakka.world.World
import org.scalatest._

import scala.concurrent.duration.FiniteDuration


class SessionManagerTest extends TestKit(ActorSystem("system")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  var sessionManager: ActorRef = _

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  override def beforeAll {
    system.actorOf(Props[SharedLeveldbStore], "store")
    system.actorOf(Props[SharedStoreUsage])
    ClusterSharding(system).start(
      typeName = "player",
      entityProps = Player.props(),
      settings = ClusterShardingSettings(system),
      extractEntityId = ShardMessageConfiguration.extractEntityId,
      extractShardId = ShardMessageConfiguration.extractShardId
    )
    val playerProxy = ClusterSharding(system).shardRegion("player")
    sessionManager = system.actorOf(SessionManager.props(
      playerProxy,
      system.actorOf(World.props())
    ))

  }

  "SessionManager" must {
    "properly init session" in {
      sessionManager ! Player.Register("a1", "b", "c", "a1")
      expectMsg(Player.Registered("a1", "c"))
      sessionManager ! Player.Login("a1", "b", "a1")
      expectMsgClass(classOf[SessionManager.SessionCreated])
    }
  }

  "SessionManager" must {
    "accept messages with started session" in {
      sessionManager ! Player.Register("a2", "a", "a", "a2")
      expectMsg(Player.Registered("a2", "a"))
      sessionManager ! Player.Login("a2", "a", "a2")
      val sessionId = expectMsgClass(classOf[SessionManager.SessionCreated]).sessionId
      sessionManager ! SessionManager.SessionMessage(sessionId, World.GetState())
      expectMsgClass(classOf[World.State])
      sessionManager ! SessionManager.SessionMessage(sessionId, World.MoveThing("a2", "LEFT"))
      expectMsgAnyClassOf(classOf[World.CantMove], classOf[World.PositionChanged])
    }
  }

  "SessionManager" must {
    "not accept messages without session if needed" in {
      sessionManager ! Player.Register("a3", "a", "a", "a3")
      expectMsg(Player.Registered("a3", "a"))
      sessionManager ! Player.Login("a3", "a", "a3")
      expectMsgClass(classOf[SessionManager.SessionCreated])
      sessionManager ! SessionManager.SessionMessage(UUID.randomUUID(), World.GetState())
      sessionManager ! SessionManager.SessionMessage(UUID.randomUUID(), World.MoveThing("a3", "LEFT"))
      expectNoMessage(FiniteDuration(3, "seconds"))
    }
  }
}