package qoosky.cloudapi

import akka.actor.{ActorRef, Terminated}
import org.slf4j.LoggerFactory

class KeypadActor extends WebSocketHandler {

  val logger = LoggerFactory.getLogger("KeypadActor")
  val loginType = "keypad"
  var actuator: Option[ActorRef] = None
  notification = Some("""Please send your Qoosky API token in the following json format, {"token":"XXXX-XXXX-XXXX-XXXX"}""")

  def receive = {
    case WebSocketMessage(s) => {
      val res = login(s)
      notify(res._2)
      if (res._1) {
        context.become(authenticated)
        notification = Some("Waiting for your actuator device to be connected.")
      }
    }
    case x => defaultBehavior(x)
  }

  def authenticated: Receive = {
    case WebSocketMessage(s) => notification.foreach(notify(_))
    case ConnectionRequest(c) => {
      if(cid == Some(c)) {
        context.become(connected)
        notification = None
        actuator = Some(sender)
        actuator.foreach(context.watch(_))
        token.foreach(sender ! ConnectionRequest(_))
        notify("Successfully connected to your actuator device.")
      }
    }
    case x => defaultBehavior(x)
  }

  def connected: Receive = {
    case WebSocketMessage(s) => actuator.foreach(_ ! FromActorMessage(s))
    case FromActorMessage(s) => sendRaw(s)
    case Terminated(ref) if Some(ref) == actuator => {
      actuator = None
      context.become(authenticated)
      notification = Some("Disconnected. Waiting for your actuator device to be re-connected.")
    }
    case x => defaultBehavior(x)
  }
}
