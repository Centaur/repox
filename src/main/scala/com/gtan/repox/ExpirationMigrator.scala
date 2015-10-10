package com.gtan.repox

import java.nio.file.Paths

import akka.actor.{Props, ActorSystem, ActorLogging}
import akka.persistence.{DeleteMessagesSuccess, RecoveryCompleted, PersistentActor}
import com.gtan.repox.ExpirationManager.Expiration

import scala.util.Properties._

class ExpirationMigrator extends PersistentActor {

  val storagePath = Paths.get(userHome, ".repox", "storage")

  def resolveToPaths(uri: String) = (storagePath.resolve(uri.tail), storagePath.resolve(uri.tail + ".sha1"))

  println("Start migration ...")
  override def receiveRecover: Receive = {
    case e@Expiration(uri, timestamp) =>
      val (path, sha1Path) = resolveToPaths(uri)
      path.toFile.delete()
      sha1Path.toFile.delete()
      println(s"$path and $sha1Path deleted.")
    case RecoveryCompleted => // This is the last message that receiveRecover would receive
      deleteMessages(Long.MaxValue)
  }

  override def receiveCommand: Receive = {
    case DeleteMessagesSuccess(_) =>
      println("Migration finished.")
      context.system.terminate()
  }

  override def persistenceId: String = "Expiration"

}

object ExpirationMigrator {
  def main(args: Array[String]) {
    val system = ActorSystem("ExpirationMigrator")
    val migrator = system.actorOf(Props[ExpirationMigrator], "Migrator")
  }
}
