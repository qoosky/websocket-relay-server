package qoosky.cloudapi.actors

import akka.actor.{Actor, ActorSystem}
import akka.testkit.TestActorRef
import org.scalatest._

class WebSocketActor extends Actor {
  var lastMessage: String = _
  def receive: PartialFunction[Any, Unit] = {
    case WebSocketMessage(s) => lastMessage = s
    case x: Any => throw new RuntimeException("Received message other than WebSocketMessage: %s" format x)
  }
}

class SupervisorSpec extends FunSpec with BeforeAndAfter {

  implicit val system: ActorSystem = ActorSystem("test-cloudapi-system")
  val supervisorRef: TestActorRef[Supervisor] = TestActorRef[Supervisor]
  val supervisor: Supervisor = supervisorRef.underlyingActor

  before {
  }

  after {
    supervisor.actuators.foreach(_ ! Disconnected)
    supervisor.keypads.foreach(_ ! Disconnected)
  }

  describe("Test ExceptionalEvent") {
    it("Received string") {
      intercept[RuntimeException] {
        supervisorRef.receive("string")
      }
    }
    it("Received integer") {
      intercept[RuntimeException] {
        supervisorRef.receive(123)
      }
    }
  }

  describe("Actuator Management") {
    it("Save Actuator actor") {
      val actuatorRef = TestActorRef[ActuatorActor]
      val webSocketRef = TestActorRef[WebSocketActor]
      assert(supervisor.actuators.isEmpty)
      supervisorRef ! NewActuator(actuatorRef, webSocketRef)
      assertResult(1)(supervisor.actuators.size)
    }
    it("Do not save the same Actuator actor multiple times") {
      val actuatorRef = TestActorRef[ActuatorActor]
      val webSocketRef = TestActorRef[WebSocketActor]
      supervisorRef ! NewActuator(actuatorRef, webSocketRef)
      assertResult(1)(supervisor.actuators.size)
      supervisorRef ! NewActuator(actuatorRef, webSocketRef)
      assertResult(1)(supervisor.actuators.size)
    }
    it("Remove disconnected Actuator actor") {
      val actuatorRef = TestActorRef[ActuatorActor]
      val webSocketRef = TestActorRef[WebSocketActor]
      supervisorRef ! NewActuator(actuatorRef, webSocketRef)
      assertResult(1)(supervisor.actuators.size)
      actuatorRef ! Disconnected
      assert(supervisor.actuators.isEmpty)
    }
  }

  describe("Keypad Management") {
    it("Save Keypad actor") {
      val keypadRef = TestActorRef[KeypadActor]
      val webSocketRef = TestActorRef[WebSocketActor]
      assert(supervisor.keypads.isEmpty)
      supervisorRef ! NewKeypad(keypadRef, webSocketRef)
      assertResult(1)(supervisor.keypads.size)
    }
    it("Do not save the same Keypad actor multiple times") {
      val keypadRef = TestActorRef[KeypadActor]
      val webSocketRef = TestActorRef[WebSocketActor]
      supervisorRef ! NewKeypad(keypadRef, webSocketRef)
      assertResult(1)(supervisor.keypads.size)
      supervisorRef ! NewKeypad(keypadRef, webSocketRef)
      assertResult(1)(supervisor.keypads.size)
    }
    it("Remove disconnected Keypad actor") {
      val keypadRef = TestActorRef[KeypadActor]
      val webSocketRef = TestActorRef[WebSocketActor]
      supervisorRef ! NewKeypad(keypadRef, webSocketRef)
      assertResult(1)(supervisor.keypads.size)
      keypadRef ! Disconnected
      assert(supervisor.keypads.isEmpty)
    }
  }
}
