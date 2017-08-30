package qoosky.cloudapi.actors

import akka.actor.{ActorIdentity, ActorSystem}
import akka.testkit.TestActorRef
import org.scalatest._

class ActuatorActorKeypadActorSpec extends FunSpec with BeforeAndAfter {

  implicit val system: ActorSystem = ActorSystem("test-cloudapi-system")

  var actRef: TestActorRef[ActuatorActor] = _
  var act: ActuatorActor = _
  var actWsRef: TestActorRef[WebSocketActor] = _
  var actWs: WebSocketActor = _

  var keyRef: TestActorRef[KeypadActor] = _
  var key: KeypadActor = _
  var keyWsRef: TestActorRef[WebSocketActor] = _
  var keyWs: WebSocketActor = _

  val identifyId = 1
  val validToken = "XXXX-XXXX-XXXX-XXXX"

  before {
    actRef = TestActorRef[ActuatorActor]
    act = actRef.underlyingActor
    actWsRef = TestActorRef[WebSocketActor]
    actWs = actWsRef.underlyingActor
    actRef ! WebSocketInterface(actWsRef)

    keyRef = TestActorRef[KeypadActor]
    key = keyRef.underlyingActor
    keyWsRef = TestActorRef[WebSocketActor]
    keyWs = keyWsRef.underlyingActor
    keyRef ! WebSocketInterface(keyWsRef)
  }

  after {
    system.stop(actRef)
    system.stop(actWsRef)
    system.stop(keyRef)
    system.stop(keyWsRef)
  }

  describe("API Token Authorization") {
    describe("Actuator") {
      it("Success") {
        val notificationOld = act.notification
        actRef ! WebSocketMessage("""{"token": "%s"}""" format validToken)
        assert(act.notification != notificationOld)
      }
      it("Failure") {
        val notificationOld = act.notification
        actRef ! WebSocketMessage("""{"key": "%s"}""" format validToken)
        assert(act.notification == notificationOld)
      }
    }
    describe("Keypad") {
      it("Success") {
        val notificationOld = key.notification
        keyRef ! WebSocketMessage("""{"token": "%s"}""" format validToken)
        assert(key.notification != notificationOld)
      }
      it("Failure") {
        val notificationOld = key.notification
        keyRef ! WebSocketMessage("""{"key": "%s"}""" format validToken)
        assert(key.notification == notificationOld)
      }
    }
  }

  describe("Connection between Actuator and Keypad") {
    it("Success if each type has only one actor") {
      actRef ! WebSocketMessage("""{"token": "%s"}""" format validToken)
      keyRef ! WebSocketMessage("""{"token": "%s"}""" format validToken)
      actRef ! ActorIdentity(identifyId, Some(keyRef))
      assertResult(None)(act.notification)
      assertResult(None)(key.notification)
      assertResult(Some(keyRef))(act.keypad)
      assertResult(Some(actRef))(key.actuator)
    }
  }

  describe("Bi-directional communication") {
    it("Message passing between WebSocketInterfaces of Actuator and Keypad") {
      actRef ! WebSocketMessage("""{"token": "%s"}""" format validToken)
      keyRef ! WebSocketMessage("""{"token": "%s"}""" format validToken)
      actRef ! ActorIdentity(identifyId, Some(keyRef))
      actRef ! WebSocketMessage("""{"key1":"val1"}""")
      assertResult("""{"key1":"val1"}""")(keyWs.lastMessage)
      keyRef ! WebSocketMessage("""{"key2":"val2"}""")
      assertResult("""{"key2":"val2"}""")(actWs.lastMessage)
    }
    it("Re-connection of disconnected Actuator") {
      actRef ! WebSocketMessage("""{"token": "%s"}""" format validToken)
      keyRef ! WebSocketMessage("""{"token": "%s"}""" format validToken)
      actRef ! ActorIdentity(identifyId, Some(keyRef))
      actRef ! Disconnected
      Thread.sleep(100)
      assert(key.actuator.isEmpty)
      assert(key.notification.isDefined)
      actRef = TestActorRef[ActuatorActor]
      act = actRef.underlyingActor
      actWsRef = TestActorRef[WebSocketActor]
      actWs = actWsRef.underlyingActor
      actRef ! WebSocketInterface(actWsRef)
      actRef ! WebSocketMessage("""{"token": "%s"}""" format validToken)
      actRef ! ActorIdentity(identifyId, Some(keyRef))
      assertResult(None)(act.notification)
      assertResult(None)(key.notification)
      assertResult(Some(keyRef))(act.keypad)
      assertResult(Some(actRef))(key.actuator)
    }
    it("Re-connection of disconnected Keypad") {
      actRef ! WebSocketMessage("""{"token": "%s"}""" format validToken)
      keyRef ! WebSocketMessage("""{"token": "%s"}""" format validToken)
      actRef ! ActorIdentity(identifyId, Some(keyRef))
      keyRef ! Disconnected
      Thread.sleep(100)
      assert(act.keypad.isEmpty)
      assert(act.notification.isDefined)
      keyRef = TestActorRef[KeypadActor]
      key = keyRef.underlyingActor
      keyWsRef = TestActorRef[WebSocketActor]
      keyWs = keyWsRef.underlyingActor
      keyRef ! WebSocketInterface(keyWsRef)
      keyRef ! WebSocketMessage("""{"token": "%s"}""" format validToken)
      actRef ! ActorIdentity(identifyId, Some(keyRef))
      assertResult(None)(act.notification)
      assertResult(None)(key.notification)
      assertResult(Some(keyRef))(act.keypad)
      assertResult(Some(actRef))(key.actuator)
    }
  }

  describe("The same token is used twice for the same login type") {
    it("New Actuator's ConnectionRequest is ignored") {
      actRef ! WebSocketMessage("""{"token": "%s"}""" format validToken)
      keyRef ! WebSocketMessage("""{"token": "%s"}""" format validToken)
      actRef ! ActorIdentity(identifyId, Some(keyRef))
      assertResult(None)(act.notification)
      assertResult(None)(key.notification)
      assertResult(Some(keyRef))(act.keypad)
      assertResult(Some(actRef))(key.actuator)
      val actRef2 = TestActorRef[ActuatorActor]
      val act2 = actRef2.underlyingActor
      val actWsRef2 = TestActorRef[WebSocketActor]
      actRef2 ! WebSocketInterface(actWsRef2)
      actRef2 ! WebSocketMessage("""{"token": "%s"}""" format validToken)
      actRef2 ! ActorIdentity(identifyId, Some(keyRef))
      assert(act2.notification.isDefined)
      assert(act2.keypad.isEmpty)
      assertResult(None)(act.notification)
      assertResult(None)(key.notification)
      assertResult(Some(keyRef))(act.keypad)
      assertResult(Some(actRef))(key.actuator)
    }
    it("Only first Keypad can be connected") {
      actRef ! WebSocketMessage("""{"token": "%s"}""" format validToken)
      keyRef ! WebSocketMessage("""{"token": "%s"}""" format validToken)
      val keyRef2 = TestActorRef[KeypadActor]
      val keyWsRef2 = TestActorRef[WebSocketActor]
      keyRef2 ! WebSocketInterface(keyWsRef2)
      keyRef2 ! WebSocketMessage("""{"token": "%s"}""" format validToken)
      keyRef.receive(ConnectionRequest(validToken), actRef)
      assertResult(None)(act.notification)
      assertResult(None)(key.notification)
      assertResult(Some(keyRef))(act.keypad)
      assertResult(Some(actRef))(key.actuator)
      intercept[RuntimeException] {
        keyRef2.receive(ConnectionRequest(validToken), actRef)
      }
    }
  }
}
