package qoosky.websocketrelayserver.actors

import akka.actor.{Actor, ActorRef, Terminated}
import org.slf4j.{Logger, LoggerFactory}

class Supervisor extends Actor {

  val logger: Logger = LoggerFactory.getLogger("Supervisor")

  var actuators = Set.empty[ActorRef]
  var keypads = Set.empty[ActorRef]

  def receive: PartialFunction[Any, Unit] = {
    case NewActuator(actuator, webSocket) =>
      actuator ! WebSocketInterface(webSocket)
      context.watch(actuator)
      actuators += actuator
      logger.info("Connected: %s (%d actuators, %d keypads)" format (actuator, actuators.size, keypads.size))
    case NewKeypad(keypad, webSocket) =>
      keypad ! WebSocketInterface(webSocket)
      context.watch(keypad)
      keypads += keypad
      logger.info("Connected: %s (%d actuators, %d keypads)" format (keypad, actuators.size, keypads.size))
    case Terminated(actor) =>
      actuators -= actor
      keypads -= actor
      logger.info("Disconnected: %s (%d actuators, %d keypads)" format (actor, actuators.size, keypads.size))
    case x: Any =>
      throw new RuntimeException("Unexpected message: %s" format x)
  }
}

trait ActorMessage
case class NewActuator(actuator: ActorRef, webSocket: ActorRef) extends ActorMessage
case class NewKeypad(keypad: ActorRef, webSocket: ActorRef) extends ActorMessage
case class Disconnected() extends ActorMessage
case class WebSocketMessage(content: String) extends ActorMessage
case class FromActorMessage(content: String) extends ActorMessage
case class WebSocketInterface(webSocket: ActorRef) extends ActorMessage
case class SendStatusNotification() extends ActorMessage
case class SearchKeypads() extends ActorMessage
case class ConnectionRequest(connectionId: String) extends ActorMessage
