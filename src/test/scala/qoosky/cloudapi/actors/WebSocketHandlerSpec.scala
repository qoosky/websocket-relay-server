package qoosky.cloudapi.actors

import akka.actor.{ActorSystem, IllegalActorStateException}
import akka.testkit.TestActorRef
import org.scalatest._
import org.slf4j.{Logger, LoggerFactory}

class WebSocketHandlerImpl extends WebSocketHandler {
  val logger: Logger = LoggerFactory.getLogger("WebSocketHandlerImpl")
  val loginType = "dummy"
  def receive: PartialFunction[Any, Unit] = {
    case x => defaultBehavior(x)
  }
}

class WebSocketHandlerSpec extends FunSpec with BeforeAndAfter {

  implicit val system: ActorSystem = ActorSystem("test-cloudapi-system")
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

  describe("Test Exceptional event") {
    it("Stop automatically if receives a message when WebSocketInterface is None") {
      wsHandler.notify("message")
      intercept[IllegalActorStateException] {
        assertResult(null)(wsHandlerRef.underlyingActor)
      }
    }
  }

  describe("Test notify") {
    it("Send string") {
      wsHandlerRef ! WebSocketInterface(wsInterfaceRef)
      wsHandler.notify("message123")
      assertResult("""{"notification":"message123"}""")(wsInterface.lastMessage)
      wsHandler.sendRaw("""{"notification":"message123"}""")
      assertResult("""{"notification":"message123"}""")(wsInterface.lastMessage)
    }
  }

  describe("Scheduled message, SendStatusNotification test") {
    it("Do not send if None") {
      wsHandlerRef ! WebSocketInterface(wsInterfaceRef)
      wsHandler.notification = None
      wsHandlerRef ! SendStatusNotification
      assertResult(null)(wsInterface.lastMessage)
    }
    it("Send if not None") {
      wsHandlerRef ! WebSocketInterface(wsInterfaceRef)
      wsHandler.notification = Some("hello")
      wsHandlerRef ! SendStatusNotification
      assertResult("""{"notification":"hello"}""")(wsInterface.lastMessage)
    }
  }

  describe("WebSocketInterface management") {
    it("Save") {
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
    it("WebSocketInterface also stops when disconnected") {
      wsHandlerRef ! WebSocketInterface(wsInterfaceRef)
      wsHandlerRef ! Disconnected
      intercept[RuntimeException] {
        wsHandler.notify("message")
      }
    }
  }

  // describe("setCid/logout test") {
  //   it("first setCid") {
  //     assert(actuator.setCid(tokenValidEnabled))
  //     assert(keypad.setCid(tokenValidEnabled))
  //     assertResult(Some(1))(actuator.cid)
  //     assertResult(Some(2))(keypad.cid)
  //   }
  //   it("after setCid") {
  //     assert(actuator.setCid(tokenValidEnabled))
  //     assert(!actuator.setCid(tokenValidEnabled))
  //     assert(keypad.setCid(tokenValidEnabled))
  //     assert(!keypad.setCid(tokenValidEnabled))
  //   }
  //   it("setCid after logout") {
  //     assert(actuator.setCid(tokenValidEnabled))
  //     assert(actuator.logout)
  //     assert(actuator.setCid(tokenValidEnabled))
  //     assert(keypad.setCid(tokenValidEnabled))
  //     assert(keypad.logout)
  //     assert(keypad.setCid(tokenValidEnabled))
  //   }
  //   it("logout without setCid") {
  //     assert(!actuator.logout)
  //     assert(!keypad.logout)
  //   }
  //   it("cid becomes None if logout") {
  //     assert(actuator.setCid(tokenValidEnabled))
  //     assert(keypad.setCid(tokenValidEnabled))
  //     assertResult(Some(1))(actuator.cid)
  //     assertResult(Some(2))(keypad.cid)
  //     assert(actuator.logout)
  //     assert(keypad.logout)
  //     assertResult(None)(actuator.cid)
  //     assertResult(None)(keypad.cid)
  //   }
  //   it("setCid fails if the same token is used for the same login type") {
  //     assert(actuator.setCid(tokenValidEnabled))
  //     assert(!actuator2.setCid(tokenValidEnabled))
  //     assertResult(Some(1))(actuator.cid)
  //     assertResult(None)(actuator2.cid)
  //     assert(keypad.setCid(tokenValidEnabled))
  //     assert(!keypad2.setCid(tokenValidEnabled))
  //     assertResult(Some(2))(keypad.cid)
  //     assertResult(None)(keypad2.cid)
  //     assert(actuator.logout)
  //     assert(!actuator2.logout)
  //     assert(keypad.logout)
  //     assert(!keypad2.logout)
  //   }
  //   it("setCid fails if invalid token is provided") {
  //     assert(!actuator.setCid(tokenInvalid))
  //     assert(!keypad.setCid(tokenInvalid))
  //     assertResult(None)(actuator.cid)
  //     assertResult(None)(keypad.cid)
  //   }
  //   describe("valid but yet used to update DB record on login session is provided") {
  //     it("Actuator can setCid") {
  //       assert(actuator.setCid(tokenValidNotEnabled))
  //       assertResult(Some(1))(actuator.cid)
  //     }
  //     it("Keypad cannot setCid") {
  //       assert(!keypad.setCid(tokenValidNotEnabled))
  //       assertResult(None)(keypad.cid)
  //     }
  //   }
  // }

  // describe("searchPairCid test") {
  //   it("search setCid completed pair") {
  //     assert(actuator.setCid(tokenValidEnabled))
  //     assert(keypad.setCid(tokenValidEnabled))
  //     assertResult(keypad.cid)(actuator.searchPairCid(tokenValidEnabled))
  //     assertResult(actuator.cid)(keypad.searchPairCid(tokenValidEnabled))
  //   }
  //   it("pair does not exist") {
  //     assertResult(None)(actuator.searchPairCid(tokenValidEnabled))
  //     assertResult(None)(keypad.searchPairCid(tokenValidEnabled))
  //   }
  //   it("pair does not exist if logout") {
  //     assert(actuator.setCid(tokenValidEnabled))
  //     assert(keypad.setCid(tokenValidEnabled))
  //     assertResult(keypad.cid)(actuator.searchPairCid(tokenValidEnabled))
  //     assertResult(actuator.cid)(keypad.searchPairCid(tokenValidEnabled))
  //     assert(keypad.logout)
  //     assertResult(None)(actuator.searchPairCid(tokenValidEnabled))
  //     assert(actuator.logout)
  //     assertResult(None)(keypad.searchPairCid(tokenValidEnabled))
  //   }
  // }

  // describe("searchComradeCid test") {
  //   it("search setCid completed comrade") {
  //     assert(actuator.setCid(tokenValidEnabled))
  //     assert(keypad.setCid(tokenValidEnabled))
  //     assertResult(actuator.cid)(actuator2.searchComradeCid(tokenValidEnabled))
  //     assertResult(keypad.cid)(keypad2.searchComradeCid(tokenValidEnabled))
  //   }
  //   it("comrade does not exist") {
  //     assertResult(None)(actuator.searchComradeCid(tokenValidEnabled))
  //     assertResult(None)(keypad.searchComradeCid(tokenValidEnabled))
  //   }
  //   it("self cid cannot be searched") {
  //     assert(actuator.setCid(tokenValidEnabled))
  //     assert(keypad.setCid(tokenValidEnabled))
  //     assertResult(None)(actuator.searchComradeCid(tokenValidEnabled))
  //     assertResult(None)(keypad.searchComradeCid(tokenValidEnabled))
  //   }
  //   it("comrade does not exist if logout") {
  //     assert(actuator.setCid(tokenValidEnabled))
  //     assert(keypad.setCid(tokenValidEnabled))
  //     assertResult(actuator.cid)(actuator2.searchComradeCid(tokenValidEnabled))
  //     assertResult(keypad.cid)(keypad2.searchComradeCid(tokenValidEnabled))
  //     assert(actuator.logout)
  //     assertResult(None)(actuator2.searchComradeCid(tokenValidEnabled))
  //     assert(keypad.logout)
  //     assertResult(None)(keypad2.searchComradeCid(tokenValidEnabled))
  //   }
  // }

  // describe("login test") {
  //   it("Valid json API token is provided") {
  //     assert(actuator.login("""{"token": "%s"}""" format tokenValidEnabled)._1)
  //     assertResult(Some(tokenValidEnabled))(actuator.token)
  //   }
  //   it("Valid json but invalid API token is provided") {
  //     assert(!actuator.login("""{"key": "%s"}""" format tokenValidEnabled)._1)
  //     assertResult(None)(actuator.token)
  //   }
  //   it("Invalid json string") {
  //     assert(!actuator.login("mystring")._1)
  //     assertResult(None)(actuator.token)
  //   }
  // }
}
