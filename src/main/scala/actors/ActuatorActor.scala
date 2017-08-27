package qoosky.cloudapi

import akka.actor.{ActorIdentity, ActorRef, Cancellable, Identify, Terminated}
import org.slf4j.LoggerFactory
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

class ActuatorActor extends WebSocketHandler {

  val logger = LoggerFactory.getLogger("ActuatorActor")
  val loginType = "actuator"
  val identifyId = 1
  var keypad: Option[ActorRef] = None
  var schedule: Option[Cancellable] = None
  notification = Some("""Please send your Qoosky API token in the following json format, {"token":"XXXX-XXXX-XXXX-XXXX"}""")

  def receive = {
    case WebSocketMessage(s) => {
      val res = login(s)
      notify(res._2)
      if (res._1) {
        context.become(authenticated)
        notification = Some("Searching for your cloud controller device...")
        schedule = Some(context.system.scheduler.schedule(1 seconds, 3 seconds, self, SearchKeypads))
      }
    }
    case x => defaultBehavior(x)
  }

  def authenticated: Receive = {
    case WebSocketMessage(s) => notification.foreach(notify(_))
    case SearchKeypads => context.actorSelection("/user/KeypadActor-*") ! Identify(identifyId)
    case ActorIdentity(`identifyId`, Some(ref)) => token.foreach(ref ! ConnectionRequest(_))
    case ActorIdentity(`identifyId`, None) => {}
    case ConnectionRequest(c) => {
      if(cid == Some(c)) {
        context.become(connected)
        schedule.foreach(_.cancel)
        notification = None
        keypad = Some(sender)
        keypad.foreach(context.watch(_))
        notify("Successfully connected to your cloud controller device.")
      }
    }
    case x => defaultBehavior(x)
  }

  def connected: Receive = {
    case WebSocketMessage(s) => keypad.foreach(_ ! FromActorMessage(s))
    case FromActorMessage(s) => sendRaw(s)
    case Terminated(ref) if Some(ref) == keypad => {
      keypad = None
      context.become(authenticated)
      notification = Some("Disconnected. Re-searching for your cloud controller device...")
      schedule = Some(context.system.scheduler.schedule(1 seconds, 3 seconds, self, SearchKeypads))
    }
    case x => defaultBehavior(x)
  }
}
