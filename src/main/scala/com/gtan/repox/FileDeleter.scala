package com.gtan.repox

import akka.actor.{PoisonPill, Actor, ActorLogging}

object FileDeleter {
  case object Quarantined
}

/**
 * delete file and sha1 file as a whole
 * @param uri
 */
class FileDeleter(uri: String, initiator: Symbol) extends Actor with ActorLogging {
  import FileDeleter._

  log.debug(s"$initiator initiated deletion of $uri")
  Repox.requestQueueMaster ! RequestQueueMaster.Quarantine(uri: String)
  override def receive = {
    case Quarantined =>
      val (path, sha1Path) = Repox.resolveToPaths(uri)
      path.toFile.delete()
      sha1Path.toFile.delete()
      Repox.requestQueueMaster ! RequestQueueMaster.FileDeleted(uri)
      log.debug(s"$path and $sha1Path deleted.")
      self ! PoisonPill
  }
}
