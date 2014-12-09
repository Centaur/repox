package com.gtan.repox

import akka.actor.{ActorLogging, Cancellable, Props}
import akka.persistence.PersistentActor
import org.joda.time.DateTime

import scala.concurrent.duration._
import scala.language.postfixOps

object ExpirationPersister {

  case class CreateExpiration(uri: String, duration: Duration)

  case class CancelExpiration(uri: String)

  case class PerformExpiration(uri: String)

}

case class Expiration(uri: String, timestamp: DateTime)

class ExpirationPersister extends PersistentActor with ActorLogging {

  import com.gtan.repox.ExpirationPersister._

  import scala.concurrent.ExecutionContext.Implicits.global

  override def persistenceId: String = "Expiration"

  var scheduledExpirations: Map[String, Cancellable] = _

  def scheduleFileDelete(expiration: Expiration): Unit = {
    if (expiration.timestamp.isAfterNow) {
      val cancellable = Repox.system.scheduler.scheduleOnce(
        expiration.timestamp.getMillis - DateTime.now().getMillis millis,
        self,
        PerformExpiration(expiration.uri))
      scheduledExpirations = scheduledExpirations.updated(expiration.uri, cancellable)
    } else {
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
      persist(expiration) { _ =>}
      scheduleFileDelete(expiration)
    case CancelExpiration(pattern) =>
      persist(CancelExpiration(pattern)) { _ =>}
      cancelExpirations(pattern)
    case PerformExpiration(uri) =>
      context.actorOf(Props(classOf[FileDeleter], uri, 'ExpirationPersister))
      scheduledExpirations = scheduledExpirations - uri
  }

}
