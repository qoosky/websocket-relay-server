package qoosky.websocketrelayserver

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.OverflowStrategy
import akka.stream.scaladsl._
import org.slf4j.{Logger, LoggerFactory}
import qoosky.websocketrelayserver.actors._

class WebService(system: ActorSystem) {

  val logger: Logger = LoggerFactory.getLogger("WebService")
  val webSocketBufferSize = 5
  var actuatorId = 0
  var keypadId = 0

  val supervisor: ActorRef = system.actorOf(Props[Supervisor], name = "Supervisor")

  val fromWebSocket: Flow[Message, ActorMessage, _] = Flow[Message].collect {
    case TextMessage.Strict(txt) => WebSocketMessage(txt) // Ignore incomplete (part of a large message) or binary messages.
  }

  val backToWebSocket: Flow[ActorMessage, Message, _] = Flow[ActorMessage].map {
    case WebSocketMessage(txt) => TextMessage(txt)
    case x: ActorMessage =>
      logger.error("Expected WebSocketMessage, but received ActorMessage unexpectedly: %s" format x)
      throw new RuntimeException
  }

  // Raspberry Pi, Arduino, Android Things, etc...
  def actuatorWebSocketService: Flow[Message, Message, _] = {
    val actuator: ActorRef = system.actorOf(Props[ActuatorActor], name = "ActuatorActor-%d".format(actuatorId))
    val fromActor: Source[ActorMessage, _] = Source.actorRef[ActorMessage](bufferSize = webSocketBufferSize, OverflowStrategy.dropHead).mapMaterializedValue{ webSocket: ActorRef =>
      supervisor ! NewActuator(actuator, webSocket)
    }
    val toActor: Sink[ActorMessage, _] = Sink.actorRef[ActorMessage](actuator, Disconnected)
    val in: Sink[Message, _] = Flow[Message].via(fromWebSocket).to(toActor)
    val out: Source[Message, _] = fromActor.via(backToWebSocket)
    actuatorId += 1
    Flow.fromSinkAndSource(in, out)
  }

  // JavaScript Client (Hosted on qoosky.dev)
  def keypadWebSocketService: Flow[Message, Message, _] = {
    val keypad: ActorRef = system.actorOf(Props[KeypadActor], name = "KeypadActor-%d".format(keypadId))
    val fromActor: Source[ActorMessage, _] = Source.actorRef[ActorMessage](bufferSize = webSocketBufferSize, OverflowStrategy.dropHead).mapMaterializedValue{ webSocket: ActorRef =>
      supervisor ! NewKeypad(keypad, webSocket)
    }
    val toActor: Sink[ActorMessage, _] = Sink.actorRef[ActorMessage](keypad, Disconnected)
    val in: Sink[Message, _] = Flow[Message].via(fromWebSocket).to(toActor)
    val out: Source[Message, _] = fromActor.via(backToWebSocket)
    keypadId += 1
    Flow.fromSinkAndSource(in, out)
  }

  def route: Route = path("") {
    get {
      complete("The latest API version is /v1")
    }
  } ~
  pathPrefix("v1") {
    pathEndOrSingleSlash {
      complete("Available API endpoints: /v1/websocket-relay-server/actuator/ws")
    } ~
    pathPrefix("websocket-relay-server" / "actuator" / "ws") {
      pathEndOrSingleSlash {
        handleWebSocketMessages(actuatorWebSocketService)
      }
    } ~
    pathPrefix("websocket-relay-server" / "keypad" / "ws") {
      pathEndOrSingleSlash {
        handleWebSocketMessages(keypadWebSocketService)
      }
    }
  }
}
