package qoosky.cloudapi.actors

import akka.actor.{Actor, ActorIdentity, ActorRef, Terminated}
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

trait WebSocketHandler extends Actor {

  val logger: org.slf4j.Logger
  val loginType: String // TODO
  val identifyIdPurge = 2 // TODO
  private var webSocket: Option[ActorRef] = None
  var notification: Option[String] = None
  var comradeCid: Option[String] = None // TODO
  var cid: Option[String] = None

  context.system.scheduler.schedule(2 seconds, 1 second, self, SendStatusNotification)

  def sendRaw(str: String): Unit = {
    webSocket match {
      case Some(ref) => ref ! WebSocketMessage(str)
      case None =>
        logger.error("sendRaw failed. webSocket actor is not set. something is wrong. stopping...")
        context.stop(self)
    }
  }

  def notify(str: String): Unit = {
    val json: String = Map("notification" -> str).toJson.compactPrint
    sendRaw(json)
  }

  def login(jsonStr: String): (Boolean, String) = {
    try {
      val jsonMap = jsonStr.parseJson.convertTo[Map[String, String]]
      jsonMap.get("token") match {
        case Some(t) =>
          if(cid.isEmpty) {
            cid = Some(t)
            (true, "Login success.")
          }
          else (false, "Already logged in.")
        case None => (false, "`token` is missing. Please provide your Qoosky API token in the specified json format.")
      }
    } catch {
      case e: Exception =>
        logger.warn("Invalid API token was provided: %s" format e)
        (false, "json format is invalid. Please provide your Qoosky API token in the specified format.")
    }
  }

  def defaultBehavior(msg: Any): Unit = {
    msg match {
      case SendStatusNotification => notification.foreach(notify)
      case WebSocketInterface(ref) =>
        webSocket = Some(ref)
        context.watch(ref)
      case ActorIdentity(`identifyIdPurge`, Some(ref)) => comradeCid.foreach(ref ! DisconnectionRequest(_)) // TODO
      case ActorIdentity(`identifyIdPurge`, None) => // TODO
      case Disconnected =>
        logger.info("stopping... %s" format self)
        webSocket.foreach { ref =>
          context.stop(ref)
          webSocket = None
        }
        context.stop(self)
      case DisconnectionRequest(c) => // TODO
        if(cid.contains(c)) {
          notify("Detected another device. Disconnecting...")
          self ! Disconnected
        }
      case Terminated(ref) => if (webSocket.contains(ref)) webSocket = None
      case x: Any => logger.warn("Received unexpected message: %s from %s" format(x, sender))
    }
  }
}
