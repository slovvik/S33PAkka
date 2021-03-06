package org.byern.s33pakka.player

import akka.actor.{ActorLogging, Props}
import akka.persistence.PersistentActor
import com.fasterxml.jackson.annotation.JsonProperty
import org.byern.s33pakka.core.{Message, Persistable, ShardMessage}
import org.byern.s33pakka.dto.{ClientMessage, ClientResponse}
import org.byern.s33pakka.player.Player._

object Player {

  trait PlayerMsg extends Message

  case class Register(
                       @JsonProperty("login")
                       login: String,
                       @JsonProperty("password")
                       password: String,
                       @JsonProperty("sign")
                       sign: String,
                       entityId: String) extends ShardMessage()
    with PlayerMsg with ClientMessage

  case class Registered(login: String, sign: String, override val msgType: String = "REGISTERED")
    extends ClientResponse

  case class TooLongSign(login: String, sign: String, override val msgType: String = "TOO_LONG_SIGN")
    extends ClientResponse

  case class AlreadyRegistered(login: String, override val msgType: String = "ALREADY_EXISTS")
    extends ClientResponse

  case class NotExists(login: String, override val msgType: String = "NOT_FOUND")
    extends ClientResponse

  case class NotInitialized()

  case class Login(
                    @JsonProperty("login")
                    login: String,
                    @JsonProperty("password")
                    password: String,
                    entityId: String) extends ShardMessage
    with PlayerMsg with ClientMessage

  case class IncorrectPassword(login: String, override val msgType: String = "INCORRECT_PASSWORD")
    extends ClientResponse

  case class CorrectPassword(login: String, sign: String)

  case class InitializedEvent(login: String, password: String, sign: String) extends Persistable

  def props(): Props = Props(new Player())
}

class Player extends PersistentActor with ActorLogging {

  var login: String = ""
  var password: String = ""
  var sign: String = ""

  override def persistenceId = self.path.parent.name + "-" + self.path.name

  override def receiveRecover = {
    case evt: Persistable =>
      updateState(evt)
  }

  def notInitialized: Receive = {
    case Register(login: String, password: String, sign: String, _) =>
      if (sign.length == 1) {
        persist(InitializedEvent(login, password, sign)) { event =>
          updateState(event)
        }
        sender() ! Registered(login, sign)
      } else {
        sender() ! TooLongSign(login, sign)
      }
    case Login(login: String, _, _) =>
      sender() ! NotExists(login)
    case _ =>
      sender() ! NotInitialized()
  }

  def initialized: Receive = {
    case Login(_, password: String, _) =>
      if (this.password == password)
        sender() ! CorrectPassword(login, sign)
      else
        sender() ! IncorrectPassword(login)
    case msg:Register =>
      sender() ! AlreadyRegistered(login)
    case msg@_ =>
      log.info("Unknown message " + msg)
  }

  override def receiveCommand = notInitialized

  def updateState(event: Persistable): Unit = event match {
    case InitializedEvent(login: String, password: String, sign: String) =>
      this.login = login
      this.password = password
      this.sign = sign
      context become initialized
  }
}
