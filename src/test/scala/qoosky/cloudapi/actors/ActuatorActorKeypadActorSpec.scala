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

  val token = "XXXX-XXXX-XXXX-XXXX"

  val identifyId = 1
  val identifyIdPurge = 2

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
        actRef ! WebSocketMessage("""{"token": "%s"}""" format token)
        assert(act.notification != notificationOld)
      }
      it("Failure") {
        val notificationOld = act.notification
        actRef ! WebSocketMessage("""{"key": "%s"}""" format token)
        assert(act.notification == notificationOld)
      }
    }
    describe("Keypad") {
      it("Success") {
        val notificationOld = key.notification
        keyRef ! WebSocketMessage("""{"token": "%s"}""" format token)
        assert(key.notification != notificationOld)
      }
      it("Failure") {
        val notificationOld = key.notification
        keyRef ! WebSocketMessage("""{"key": "%s"}""" format token)
        assert(key.notification == notificationOld)
      }
    }
  }

  describe("Connection test of Actuator and Keypad") {
    it("Success if each type has only one actor") {
      actRef ! WebSocketMessage("""{"token": "%s"}""" format token)
      keyRef ! WebSocketMessage("""{"token": "%s"}""" format token)
      actRef ! ActorIdentity(identifyId, Some(keyRef))
      assertResult(None)(act.notification)
      assertResult(None)(key.notification)
      assertResult(Some(keyRef))(act.keypad)
      assertResult(Some(actRef))(key.actuator)
    }
  }

  describe("Bi-directional communication test") {
    it("Success if each type has only one actor") {
      actRef ! WebSocketMessage("""{"token": "%s"}""" format token)
      keyRef ! WebSocketMessage("""{"token": "%s"}""" format token)
      actRef ! ActorIdentity(identifyId, Some(keyRef))
      actRef ! WebSocketMessage("""{"key1":"val1"}""")
      assertResult("""{"key1":"val1"}""")(keyWs.lastMessage)
      keyRef ! WebSocketMessage("""{"key2":"val2"}""")
      assertResult("""{"key2":"val2"}""")(actWs.lastMessage)
    }
    it("Restore when Actuator is disconnected") {
      actRef ! WebSocketMessage("""{"token": "%s"}""" format token)
      keyRef ! WebSocketMessage("""{"token": "%s"}""" format token)
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
      actRef ! WebSocketMessage("""{"token": "%s"}""" format token)
      actRef ! ActorIdentity(identifyId, Some(keyRef))
      assertResult(None)(act.notification)
      assertResult(None)(key.notification)
      assertResult(Some(keyRef))(act.keypad)
      assertResult(Some(actRef))(key.actuator)
    }
    it("Restore when Keypad is disconnected") {
      actRef ! WebSocketMessage("""{"token": "%s"}""" format token)
      keyRef ! WebSocketMessage("""{"token": "%s"}""" format token)
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
      keyRef ! WebSocketMessage("""{"token": "%s"}""" format token)
      actRef ! ActorIdentity(identifyId, Some(keyRef))
      assertResult(None)(act.notification)
      assertResult(None)(key.notification)
      assertResult(Some(keyRef))(act.keypad)
      assertResult(Some(actRef))(key.actuator)
    }
  }

  describe("The same token is used twice with the same login type") {
    // describe("If remaining thread exists, request the existing actor to logout") {
    //   it("Actuator") {
    //     actRef ! WebSocketMessage("""{"token": "%s"}""" format token)
    //     keyRef ! WebSocketMessage("""{"token": "%s"}""" format token)
    //     actRef ! ActorIdentity(identifyId, Some(keyRef))
    //     assertResult(None)(act.notification)
    //     assertResult(None)(key.notification)
    //     assertResult(Some(keyRef))(act.keypad)
    //     assertResult(Some(actRef))(key.actuator)
    //     val actRef2 = TestActorRef[ActuatorActor]
    //     val act2 = actRef2.underlyingActor
    //     val actWsRef2 = TestActorRef[WebSocketActor]
    //     actRef2 ! WebSocketInterface(actWsRef2)
    //     actRef2 ! WebSocketMessage("""{"token": "%s"}""" format token)
    //     actRef2 ! ActorIdentity(identifyIdPurge, Some(actRef))
    //     actRef2 ! ActorIdentity(identifyId, Some(keyRef))
    //     assertResult(None)(act2.notification)
    //     assertResult(None)(key.notification)
    //     assertResult(Some(keyRef))(act2.keypad)
    //     assertResult(Some(actRef2))(key.actuator)
    //   }
    //   it("Keypad") {
    //     actRef ! WebSocketMessage("""{"token": "%s"}""" format token)
    //     keyRef ! WebSocketMessage("""{"token": "%s"}""" format token)
    //     actRef ! ActorIdentity(identifyId, Some(keyRef))
    //     assertResult(None)(act.notification)
    //     assertResult(None)(key.notification)
    //     assertResult(Some(keyRef))(act.keypad)
    //     assertResult(Some(actRef))(key.actuator)
    //     val keyRef2 = TestActorRef[KeypadActor]
    //     val key2 = keyRef2.underlyingActor
    //     val keyWsRef2 = TestActorRef[WebSocketActor]
    //     keyRef2 ! WebSocketInterface(keyWsRef2)
    //     keyRef2 ! WebSocketMessage("""{"token": "%s"}""" format token)
    //     keyRef2 ! ActorIdentity(identifyIdPurge, Some(keyRef))
    //     actRef ! ActorIdentity(identifyId, Some(keyRef2))
    //     assertResult(None)(act.notification)
    //     assertResult(None)(key2.notification)
    //     assertResult(Some(keyRef2))(act.keypad)
    //     assertResult(Some(actRef))(key2.actuator)
    //   }
    // }
    describe("If there exist DB records even if there is no thread, then force logout purgeRetry times") {
      it("Actuator") {
        actRef ! WebSocketMessage("""{"token": "%s"}""" format token)
        system.stop(actRef)
        Thread.sleep(100)
        actRef = TestActorRef[ActuatorActor]
        act = actRef.underlyingActor
        actWsRef = TestActorRef[WebSocketActor]
        actWs = actWsRef.underlyingActor
        actRef ! WebSocketInterface(actWsRef)
        val notificationOld = act.notification
        actRef ! WebSocketMessage("""{"token": "%s"}""" format token)
        assert(act.notification != notificationOld)
      }
      it("Keypad") {
        keyRef ! WebSocketMessage("""{"token": "%s"}""" format token)
        system.stop(keyRef)
        Thread.sleep(100)
        keyRef = TestActorRef[KeypadActor]
        key = keyRef.underlyingActor
        keyWsRef = TestActorRef[WebSocketActor]
        keyWs = keyWsRef.underlyingActor
        keyRef ! WebSocketInterface(keyWsRef)
        val notificationOld = key.notification
        keyRef ! WebSocketMessage("""{"token": "%s"}""" format token)
        assert(key.notification != notificationOld)
      }
    }
  }
}
