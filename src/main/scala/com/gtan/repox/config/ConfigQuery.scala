package com.gtan.repox.config

import akka.actor.ActorSystem
import akka.persistence.query.{EventEnvelope, PersistenceQuery}
import akka.persistence.query.journal.leveldb.scaladsl.LeveldbReadJournal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import com.typesafe.scalalogging.LazyLogging

class ConfigQuery(val system: ActorSystem) extends LazyLogging {
  implicit val mat = ActorMaterializer()(system)

  val queries = PersistenceQuery(system).readJournalFor[LeveldbReadJournal](
  LeveldbReadJournal.Identifier)

  val src: Source[EventEnvelope, Unit] =
    queries.eventsByPersistenceId("Config", 0L, Long.MaxValue)

  val events: Source[Any, Unit] = src.map(_.event)
  events.runForeach {
    case ConfigChanged(_, cmd) => logger.debug(s"ConfigView received event caused by cmd: $cmd")
    case UseDefault => logger.debug(s"ConfigView received UseDefault evt")
  }
}
