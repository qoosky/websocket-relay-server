package qoosky.cloudapi.actors

import akka.actor.{ActorSystem, IllegalActorStateException}
import akka.testkit.TestActorRef
import org.scalatest._
import org.slf4j.{Logger, LoggerFactory}

class WebSocketHandlerImpl extends WebSocketHandler {
  val logger: Logger = LoggerFactory.getLogger("WebSocketHandlerImpl")
  def receive: PartialFunction[Any, Unit] = {
    case x => defaultBehavior(x)
  }
}

class WebSocketHandlerSpec extends FunSpec with BeforeAndAfter {

  implicit val system: ActorSystem = ActorSystem("test-cloudapi-system")
  val validToken = "XXXX-XXXX-XXXX-XXXX"
  var wsHandlerRef: TestActorRef[WebSocketHandlerImpl] = _
  var wsHandler: WebSocketHandlerImpl = _
  var wsInterfaceRef: TestActorRef[WebSocketActor] = _
  var wsInterface: WebSocketActor = _

  before {
    wsHandlerRef = TestActorRef[WebSocketHandlerImpl]
    wsHandler = wsHandlerRef.underlyingActor
    wsInterfaceRef = TestActorRef[WebSocketActor]
    wsInterface = wsInterfaceRef.underlyingActor
  }

  after {
    system.stop(wsHandlerRef)
    system.stop(wsInterfaceRef)
  }

  describe("Exceptional event") {
    it("Stop automatically if receives a message when WebSocketInterface is None") {
      wsHandler.notify("message")
      intercept[IllegalActorStateException] {
        assertResult(null)(wsHandlerRef.underlyingActor)
      }
    }
  }

  describe("notify and sendRaw") {
    it("Send string") {
      wsHandlerRef ! WebSocketInterface(wsInterfaceRef)
      wsHandler.notify("message123")
      assertResult("""{"notification":"message123"}""")(wsInterface.lastMessage)
      wsHandler.sendRaw("""{"notification":"message123"}""")
      assertResult("""{"notification":"message123"}""")(wsInterface.lastMessage)
    }
  }

  describe("Scheduled `SendStatusNotification` messages") {
    it("Do not send if notification is None") {
      wsHandlerRef ! WebSocketInterface(wsInterfaceRef)
      wsHandler.notification = None
      wsHandlerRef ! SendStatusNotification
      assertResult(null)(wsInterface.lastMessage)
    }
    it("Send if notification is not None") {
      wsHandlerRef ! WebSocketInterface(wsInterfaceRef)
      wsHandler.notification = Some("hello")
      wsHandlerRef ! SendStatusNotification
      assertResult("""{"notification":"hello"}""")(wsInterface.lastMessage)
    }
  }

  describe("login") {
    it("Valid json API token is provided") {
      assert(wsHandler.login("""{"token": "%s"}""" format validToken)._1)
      assertResult(Some(validToken))(wsHandler.token)
    }
    it("Valid json but invalid API token is provided") {
      assert(!wsHandler.login("""{"key": "%s"}""" format validToken)._1)
      assertResult(None)(wsHandler.token)
    }
    it("Invalid json string") {
      assert(!wsHandler.login("mystring")._1)
      assertResult(None)(wsHandler.token)
    }
    it("Login fails if the same token is used for the same WebSocketHandler") {
      assert(wsHandler.login("""{"token": "%s"}""" format validToken)._1)
      assertResult(Some(validToken))(wsHandler.token)
      assert(!wsHandler.login("""{"token": "%s"}""" format validToken)._1)
      assertResult(Some(validToken))(wsHandler.token)
    }
  }

  describe("WebSocketInterface management") {
    it("WebSocketHandler saves WebSocketInterface") {
      wsHandlerRef ! WebSocketInterface(wsInterfaceRef)
      wsHandler.notify("message123")
      assertResult("""{"notification":"message123"}""")(wsInterface.lastMessage)
    }
    it("WebSocketInterface stops") {
      wsHandlerRef ! WebSocketInterface(wsInterfaceRef)
      system.stop(wsInterfaceRef)
      Thread.sleep(100)
      wsHandler.notify("message")
      intercept[IllegalActorStateException] {
        assertResult(null)(wsHandlerRef.underlyingActor)
      }
    }
    it("WebSocketHandler and WebSocketInterface stop when disconnected") {
      wsHandlerRef ! WebSocketInterface(wsInterfaceRef)
      wsHandlerRef ! Disconnected
      intercept[RuntimeException] {
        wsHandler.notify("message")
      }
    }
  }
}
