package qoosky.cloudapi

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Terminated
import org.slf4j.LoggerFactory

class Supervisor extends Actor {

  val logger = LoggerFactory.getLogger("Supervisor")

  var actuators = Set.empty[ActorRef]
  var keypads = Set.empty[ActorRef]

  def receive = {
    case NewActuator(actuator, webSocket) => {
      actuator ! WebSocketInterface(webSocket)
      context.watch(actuator)
      actuators += actuator
      logger.info("new actuator connected: %s, current status: %d actuators and %d keypads" format (actuator, actuators.size, keypads.size))
    }
    case NewKeypad(keypad, webSocket) => {
      keypad ! WebSocketInterface(webSocket)
      context.watch(keypad)
      keypads += keypad
      logger.info("new keypad connected: %s, current status: %d actuators and %d keypads" format (keypad, actuators.size, keypads.size))
    }
    case Terminated(actor) => {
      actuators -= actor
      keypads -= actor
      logger.info("disconnected: %s, current status: %d actuators and %d keypads" format (actor, actuators.size, keypads.size))
    }
    case x: Any => {
      throw new RuntimeException("Unexpected message was sent to Supervisor: %s" format x)
    }
  }
}

trait ActorMessage
case class NewActuator(actuator: ActorRef, webSocket: ActorRef) extends ActorMessage
case class NewKeypad(keypad: ActorRef, webSocket: ActorRef) extends ActorMessage
case class Disconnected() extends ActorMessage
case class DisconnectionRequest(connectionId: String) extends ActorMessage
case class WebSocketMessage(content: String) extends ActorMessage
case class FromActorMessage(content: String) extends ActorMessage
case class WebSocketInterface(webSocket: ActorRef) extends ActorMessage
case class SendStatusNotification() extends ActorMessage
case class SearchKeypads() extends ActorMessage
case class ConnectionRequest(connectionId: String) extends ActorMessage
