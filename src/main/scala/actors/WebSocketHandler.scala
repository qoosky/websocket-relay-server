package qoosky.cloudapi

import akka.actor.{Actor, ActorIdentity, ActorRef, Identify, Terminated}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps
import spray.json._
import spray.json.DefaultJsonProtocol._

trait WebSocketHandler extends Actor {

  val logger: org.slf4j.Logger
  val loginType: String
  val identifyIdPurge = 2
  private var webSocket: Option[ActorRef] = None
  var notification: Option[String] = None
  var comradeCid: Option[String] = None
  var purgeRetry = 2
  var cid: Option[String] = None
  var token: Option[String] = None

  context.system.scheduler.schedule(2 seconds, 1 second, self, SendStatusNotification)

  def sendRaw(str: String): Unit = {
    webSocket match {
      case Some(ref) => ref ! WebSocketMessage(str)
      case None => {
        logger.error("sendRaw failed. webSocket actor is not set. something is wrong. stopping...")
        context.stop(self)
      }
    }
  }

  def notify(str: String): Unit = {
    val json: String = Map("notification" -> str).toJson.compactPrint
    sendRaw(json)
  }

  def login(jsonStr: String): Tuple2[Boolean, String] = {
    try {
      val jsonMap = jsonStr.parseJson.convertTo[Map[String, String]]
      jsonMap.get("token") match {
        case Some(t) => {
          if(setCid(t)) {
            token = Some(t)
            (true, "Authentication success.")
          }
          else (false, "Invalid Qoosky API token. Authentication failure.")
        }
        case None => (false, "`token` is missing. Please provide your Qoosky API token in the specified json format.")
      }
    } catch {
      case e: Throwable => {
        logger.warn("Invalid API token was provided: %s" format e)
        (false, "json format is invalid. Please provide your Qoosky API token in the specified format.")
      }
    }
  }

  def setCid(token: String): Boolean = {
    if(cid.isDefined) return false
    try {
      logger.info("validate Api Token and set cid.")
      cid = Some(token)
      logger.info("setCid success.")
      true
    } catch {
      case e: Throwable => throw new RuntimeException("setCid failure: %s" format e)
    }
  }

  def logout: Boolean = {
    cid match {
      case Some(c) => {
        cid = None // Do not delete token.
        true
      }
      case None => false
    }
  }

  def defaultBehavior(msg: Any): Unit = {
    msg match {
      case SendStatusNotification => notification.foreach(notify(_))
      case WebSocketInterface(ref) => {
        webSocket = Some(ref)
        context.watch(ref)
      }
      case ActorIdentity(`identifyIdPurge`, Some(ref)) => comradeCid.foreach(ref ! DisconnectionRequest(_))
      case ActorIdentity(`identifyIdPurge`, None) => {}
      case Disconnected => {
        logger.info("stopping... %s" format self)
        webSocket.foreach { ref =>
          context.stop(ref)
          webSocket = None
        }
        logout
        context.stop(self)
      }
      case DisconnectionRequest(c) => {
        if(cid == Some(c)) {
          notify("Detected another device. Disconnecting...")
          self ! Disconnected
        }
      }
      case Terminated(ref) => if (Some(ref) == webSocket) webSocket = None
      case x: Any => logger.warn("Unexpected message was sent: %s from %s" format(x, sender))
    }
  }
}
