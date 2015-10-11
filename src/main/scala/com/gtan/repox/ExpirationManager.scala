package com.gtan.repox

import akka.actor.{ActorLogging, Cancellable, Props}
import akka.persistence.SnapshotSelectionCriteria.Latest
import akka.persistence._
import com.gtan.repox.config.{Evt, Config, Jsonable}
import org.joda.time.DateTime
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.duration._
import scala.language.postfixOps


object ExpirationManager extends SerializationSupport {

  case class CreateExpiration(uri: String, duration: Duration)

  case class CancelExpiration(uri: String)

  case class PerformExpiration(uri: String)

  case class ExpirationPerformed(uri: String) extends Jsonable with Evt

  case class Expiration(uri: String, timestamp: DateTime) extends Jsonable with Evt

  implicit val expirationFormat = Json.format[Expiration]
  implicit val expirationPerformedFormat = Json.format[ExpirationPerformed]

  val ExpirationClass = classOf[Expiration].getName
  val ExpirationPerformedClass = classOf[ExpirationPerformed].getName

  override val reader: JsValue => PartialFunction[String, Jsonable] = payload => {
    case ExpirationClass => payload.as[Expiration]
    case ExpirationPerformedClass => payload.as[ExpirationPerformed]
  }

  override val writer: PartialFunction[Jsonable, JsValue] = {
    case o: Expiration => Json.toJson(o)
    case o: ExpirationPerformed => Json.toJson(o)
  }
}


/**
 * This actor always recover the latest snapshot so that performed or canceled expirations will not be seen in the future.
 * To exam the detailed history, use another persistence query to recovery all events.
 */
class ExpirationManager extends PersistentActor with ActorLogging {

  import com.gtan.repox.ExpirationManager._

  import scala.concurrent.ExecutionContext.Implicits.global

  override def persistenceId: String = "Expiration"

  // The following 2 states are kept in-sync during each repox process.
  // memory only
  var scheduledExpirations: Map[Expiration, Cancellable] = Map.empty
  // persistent
  var unperformed: Vector[Expiration] = Vector.empty

  def scheduleFileDelete(expiration: Expiration): Unit = {
    if (expiration.timestamp.isAfterNow) {
      val delay: Long = expiration.timestamp.getMillis - DateTime.now().getMillis
      log.debug(s"Schedule expiration for ${expiration.uri} at ${expiration.timestamp} in $delay ms")
      val cancellable = Repox.system.scheduler.scheduleOnce(
                                                             delay.millis,
                                                             self,
                                                             PerformExpiration(expiration.uri))
      scheduledExpirations = scheduledExpirations.updated(expiration, cancellable)
    } else {
      log.debug(s"${expiration.uri} expired, trigger FileDelete now.")
      context.actorOf(Props(classOf[FileDeleter], expiration.uri, 'ExpirationPersister))
    }
  }

  def cancelExpirations(pattern: String): Unit = {
    val canceled = scheduledExpirations.collect {
      case (expiration, cancellable) if expiration.uri.matches(pattern) =>
        cancellable.cancel()
        expiration
    }.toSet
    scheduledExpirations = scheduledExpirations.filterKeys(canceled.contains)
    unperformed = unperformed.filterNot(_.uri.matches(pattern))
  }

  override def receiveRecover: Receive = {
    case e@Expiration(uri, timestamp) =>
      unperformed = unperformed :+ e
      scheduleFileDelete(e)
    case CancelExpiration(pattern) =>
      cancelExpirations(pattern)
    case SnapshotOffer(metadata, saved) =>
      this.unperformed = saved.asInstanceOf[Vector[Expiration]]
      for(u <- unperformed) {
        scheduleFileDelete(u)
      }
  }

  override def receiveCommand: Receive = {
    case CreateExpiration(uri, duration) =>
      if (!scheduledExpirations.exists(_._1.uri == uri)) {
        val timestamp = DateTime.now().plusMillis(duration.toMillis.toInt)
        val expiration = Expiration(uri, timestamp)
        persist(expiration) { _ => }
        unperformed = unperformed :+ expiration
        scheduleFileDelete(expiration)
      } else {
        // this can only happen when there were multiple request in lined in GetQueueWorker stash queue
      }
    case CancelExpiration(pattern) =>
      cancelExpirations(pattern)
      persist(CancelExpiration(pattern)) { _ =>
        saveSnapshot(unperformed)
      }
    case PerformExpiration(uri) =>
      log.debug(s"$uri expired, trigger FileDelete now.")
      context.actorOf(Props(classOf[FileDeleter], uri, 'ExpirationPersister))
    case e@ExpirationPerformed(uri) =>
      scheduledExpirations = scheduledExpirations.filterKeys(_.uri != uri)
      val performed = unperformed.find(_.uri == uri)
      unperformed = unperformed.filterNot(_ == performed)
      persist(performed) { _ =>
        saveSnapshot(unperformed)
      }
  }

}
