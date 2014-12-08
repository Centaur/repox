package com.gtan.repox

import java.io.File
import java.nio.file.Path

import akka.actor.{Props, ActorLogging, Actor}
import com.google.common.hash.Hashing
import com.google.common.io.Files
import com.gtan.repox.ExpirationPersister.CreateExpiration
import com.gtan.repox.Requests.Download
import com.gtan.repox.data.ExpireRule

import scala.concurrent.duration.Duration


/**
 * Created by IntelliJ IDEA.
 * User: xf
 * Date: 14/11/29
 * Time: 下午3:24
 */
object SHA1Checker {
  case class Check(uri: String)
}
class SHA1Checker extends Actor with ActorLogging{
  import SHA1Checker._

  override def receive = {
    case Check(uri) =>
      val path = Repox.resolveToPath(uri)
      val sha1Path = path.resolveSibling(path.getFileName + ".sha1")
      val computed = Files.hash(path.toFile, Hashing.sha1()).toString
      val downloaded = scala.io.Source.fromFile(sha1Path.toFile).mkString
      if(computed != downloaded) {
        log.debug(s"$uri sha1 inconsistence detected, delete both files and redownload.")
        context.actorOf(Props(classOf[FileDeleter], uri))
      } else {
        log.debug(s"$uri sha1 check success.")
        for(ExpireRule(_, pattern, duration, _) <- Repox.lookForExpireRule(uri)) {
          Repox.expirationPersister ! CreateExpiration(uri, duration)
        }
      }
  }
}
