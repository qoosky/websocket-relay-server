package qoosky.websocketrelayserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

class Application extends ApplicationLifecycle {

  val config: Config = ConfigFactory.load
  val logger: Logger = LoggerFactory.getLogger("Application")
  val applicationName = "websocketrelayserver"

  implicit val system: ActorSystem = ActorSystem(s"$applicationName-system")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  val service: WebService = {
    new WebService(system)
  }

  var started: Boolean = false
  var bindingFuture: Option[Future[Http.ServerBinding]] = None

  def start(): Unit = {
    logger.info(s"Starting $applicationName Service")
    if (!started) {
      val defaultInterface = config.getString("qoosky.websocketrelayserver.interface")
      val defaultPort = config.getInt("qoosky.websocketrelayserver.port")
      val port = System.getProperty("qoosky.websocketrelayserver.port", defaultPort.toString).toInt
      bindingFuture = Some(Http().bindAndHandle(service.route, defaultInterface, port))
      started = true
      bindingFuture.foreach(_.onComplete {
        case Success(_) =>
          logger.info(s"Server online at http://127.0.0.1:$port/")
        case Failure(e) =>
          logger.info(s"Binding failed with ${e.getMessage}")
          system.terminate()
          started = false
      })
    }
  }

  def stop(): Unit = {
    logger.info(s"Stopping $applicationName Service")
    if (started) {
      bindingFuture.foreach(_.flatMap(_.unbind()).onComplete(_ => system.terminate()))
      started = false
    }
  }
}
