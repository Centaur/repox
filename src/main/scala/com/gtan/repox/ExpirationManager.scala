package com.gtan.repox

import akka.actor.{ActorLogging, Cancellable, Props}
import akka.persistence.PersistentActor
import com.gtan.repox.config.{Config, Cmd}
import org.joda.time.DateTime
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.duration._
import scala.language.postfixOps


object ExpirationManager extends SerializationSupport {

  // ToDo: Cmd should be decoupled with Config
  case class CreateExpiration(uri: String, duration: Duration)

  case class CancelExpiration(uri: String) extends Cmd

  implicit val cancelExpirationFormat = Json.format[CancelExpiration]

  case class PerformExpiration(uri: String)

  case class Expiration(uri: String, timestamp: DateTime) extends Cmd

  implicit val expirationFormat = Json.format[Expiration]

  val ExpirationClass = classOf[Expiration].getName
  val CancelExpirationClass = classOf[CancelExpiration].getName

  override val reader: JsValue => PartialFunction[String, Cmd] = payload => {
    case CancelExpirationClass => payload.as[CancelExpiration]
    case ExpirationClass => payload.as[Expiration]
  }

  override val writer: PartialFunction[Cmd, JsValue] = {
    case o: CancelExpiration => Json.toJson(o)
    case o: Expiration => Json.toJson(o)
  }
}


class ExpirationManager extends PersistentActor with ActorLogging {

  import com.gtan.repox.ExpirationManager._

  import scala.concurrent.ExecutionContext.Implicits.global

  override def persistenceId: String = "Expiration"

  var scheduledExpirations: Map[String, Cancellable] = Map.empty

  def scheduleFileDelete(expiration: Expiration): Unit = {
    if (expiration.timestamp.isAfterNow) {
      val delay: Long = expiration.timestamp.getMillis - DateTime.now().getMillis
      log.debug(s"Schedule expiration for ${expiration.uri} at ${expiration.timestamp} in $delay ms")
      val cancellable = Repox.system.scheduler.scheduleOnce(
        delay.millis,
        self,
        PerformExpiration(expiration.uri))
      scheduledExpirations = scheduledExpirations.updated(expiration.uri, cancellable)
    } else {
      log.debug(s"${expiration.uri} expired, trigger FileDelete now.")
      context.actorOf(Props(classOf[FileDeleter], expiration.uri, 'ExpirationPersister))
    }
  }

  def cancelExpirations(pattern: String): Unit = {
    val canceled = scheduledExpirations.collect {
      case (uri, cancellable) if uri.matches(pattern) =>
        cancellable.cancel()
        uri
    }.toSet
    scheduledExpirations = scheduledExpirations.filterKeys(canceled.contains)
  }

  override def receiveRecover: Receive = {
    case e@Expiration(uri, timestamp) =>
      scheduleFileDelete(e)
    case CancelExpiration(pattern) =>
      cancelExpirations(pattern)
  }

  override def receiveCommand: Receive = {
    case CreateExpiration(uri, duration) =>
      val timestamp = DateTime.now().plusMillis(duration.toMillis.toInt)
      val expiration = Expiration(uri, timestamp)
      persist(expiration) { _ => }
      scheduleFileDelete(expiration)
    case CancelExpiration(pattern) =>
      persist(CancelExpiration(pattern)) { _ => }
      cancelExpirations(pattern)
    case PerformExpiration(uri) =>
      log.debug(s"$uri expired, trigger FileDelete now.")
      context.actorOf(Props(classOf[FileDeleter], uri, 'ExpirationPersister))
      scheduledExpirations = scheduledExpirations - uri
  }

}
