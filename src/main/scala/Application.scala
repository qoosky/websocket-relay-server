package qoosky.cloudapi

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import scala.concurrent.Future
import scala.util.{Failure, Success}

class Application extends ApplicationLifecycle {

  val config = ConfigFactory.load
  val logger = LoggerFactory.getLogger("Application")
  val applicationName = "cloudapi"

  implicit val system = ActorSystem(s"$applicationName-system")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  val service = new WebService(system)

  var started: Boolean = false
  var bindingFuture: Option[Future[Http.ServerBinding]] = None

  def start(): Unit = {
    logger.info(s"Starting $applicationName Service")
    if (!started) {
      val defaultInterface = config.getString("qoosky.cloudapi.interface")
      val defaultPort = config.getInt("qoosky.cloudapi.port")
      val port = System.getProperty("qoosky.cloudapi.port", defaultPort.toString).toInt
      bindingFuture = Some(Http().bindAndHandle(service.route, defaultInterface, port))
      started = true
      bindingFuture.foreach(_.onComplete {
        case Success(binding) => {
          logger.info(s"Server online at http://127.0.0.1:$port/")
        }
        case Failure(e) => {
          logger.info(s"Binding failed with ${e.getMessage}")
          system.terminate()
          started = false
        }
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
