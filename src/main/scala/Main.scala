package qoosky.cloudapi

import org.apache.commons.daemon._
import org.slf4j.LoggerFactory

trait ApplicationLifecycle {
  def start(): Unit
  def stop(): Unit
}

class ApplicationDaemon extends Daemon {

  def init(daemonContext: DaemonContext): Unit = {}

  val app: ApplicationLifecycle = new Application
  def start() = app.start()
  def stop() = app.stop()
  def destroy() = app.stop()
}

object Main {
  def main(args: Array[String]): Unit = {

    // Executed when using `sbt run` during development.
    // Please use `jsvc` in production environment.

    val logger = LoggerFactory.getLogger("Main")
    val app = new ApplicationDaemon
    app.start()
    logger.info("Press RETURN to stop...")
    scala.io.StdIn.readLine()
    app.stop()
  }
}
