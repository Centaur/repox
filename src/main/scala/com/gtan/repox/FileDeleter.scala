package com.gtan.repox

import akka.actor.{PoisonPill, Actor, ActorLogging}

object FileDeleter {
  case object Quarantined
}

/**
 * delete file and sha1 file as a whole
 * @param uri
 */
class FileDeleter(uri: String) extends Actor with ActorLogging {
  import FileDeleter._

  Repox.requestQueueMaster ! RequestQueueMaster.Quarantine(uri: String)
  override def receive = {
    case Quarantined =>
      val path = Repox.resolveToPath(uri)
      val sha1Path = path.resolveSibling(path.getFileName + ".sha1")
      path.toFile.delete()
      sha1Path.toFile.delete()
      Repox.requestQueueMaster ! RequestQueueMaster.FileDeleted(uri)
      log.debug(s"$path and $sha1Path deleted.")
      self ! PoisonPill
  }
}
