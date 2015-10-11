package com.gtan.repox

import java.io.File
import java.nio.file.Path

import akka.actor._
import com.gtan.repox.data.Repo
import com.ning.http.client._

import scala.language.postfixOps


object GetWorker {

  case class UnsuccessResponseStatus(responseStatus: HttpResponseStatus)

  case class Failed(t: Throwable)

  case class Resume(upstream: Repo, tempFilePath: String, totalLength: Long)

  case class BodyPartGot(bodyPart: HttpResponseBodyPart)

  case class HeadersGot(headers: HttpResponseHeaders)

  case class Completed(path: Path, repo: Repo)

  case class PeerChosen(who: ActorRef)

  case object Cleanup

  // same effect as ReceiveTimeout
  case object LanternGiveup

  case class PartialDataReceived(length: Int)

  case class AsyncHandlerThrows(t: Throwable)

}

/**
 * 负责某一个 uri 在 某一个 upstream Repo 中的获取
 * @param upstream 上游 Repo
 * @param uri 要获取的uri
 * @param tempFilePath 已下载但未完成的临时文件路径,tempFilePath.isDefined时为续传,否则为新的下载任务
 * @param totalLength 文件的真实长度, 仅当tempFilePath.isDefined时使用
 */
class GetWorker(val upstream: Repo,
                val uri: String,
                val tempFilePath: Option[String],
                val totalLength: Long) extends Actor with Stash with ActorLogging {

  import com.gtan.repox.GetWorker._
  import scala.concurrent.duration._
  import scala.collection.JavaConverters._

  val handler = new GetAsyncHandler(uri, upstream, context.self, context.parent, tempFilePath)
  var downloaded = 0L
  var percentage = 0.0
  var contentLength = -1L
  var acceptByteRange = false

  val (connector, client) = Repox.clientOf(upstream)

  val requestHeaders = tempFilePath match {
    case None => new FluentCaseInsensitiveStringsMap()
      .add("Host", List(upstream.host).asJava)
      .add("Accept-Encoding", List("identity").asJava) // 禁止压缩, jar文件没有压缩的必要, 其它文件太小不值得.
    case Some(file) =>
      downloaded = new File(file).length()
      contentLength = totalLength
      new FluentCaseInsensitiveStringsMap()
      .add("Host", List(upstream.host).asJava)
      .add("Accept-Encoding", List("identity").asJava) // 禁止压缩
      .add("Range", List(s"bytes=$downloaded-").asJava)
  }

  val future = client.prepareGet(upstream.absolute(uri))
    .setHeaders(requestHeaders)
    .execute(handler)

  override def receive = {
    case AsyncHandlerThrows(t) =>
      log.debug(s"AsyncHandler throws -- ${t.getMessage}")
      tempFilePath match {
        case None =>
          context.parent ! Failed(t)
        case Some(path) =>
          log.debug("In a resuming, retry...")
          handler.cancel(deleteTempFile = false)
          context.parent ! Resume(upstream, path, totalLength)
      }
      self ! PoisonPill

    case Cleanup =>
      handler.cancel()
      self ! PoisonPill

    case PeerChosen(who) =>
      handler.cancel()
      self ! PoisonPill

    case ReceiveTimeout | LanternGiveup =>
      log.debug("GetWorker timeout (or lantern giveup).")
      handler.cancel(deleteTempFile = false)
      self ! PoisonPill
      if (acceptByteRange || tempFilePath.isDefined) {
        context.parent ! Resume(upstream,
                                 tempFilePath.fold(handler.tempFile.getAbsolutePath)(identity),
                                 tempFilePath.fold(contentLength)(_ => totalLength))
      } else {
        context.parent ! Failed(new RuntimeException("Chosen worker timeout or lantern giveup"))
      }

    case PartialDataReceived(length) =>
      downloaded += length
      if (contentLength != -1) {
        val newPercentage = downloaded * 100.0 / contentLength
        if (newPercentage - percentage > 10.0 || downloaded == contentLength) {
          log.debug(f"downloaded $downloaded%s bytes. $newPercentage%.2f %%")
          percentage = newPercentage
        }
      } else {
        log.debug("PartialDataReceived")
      }

    case HeadersGot(headers) =>
      tempFilePath match {
        case None =>
          val contentLengthHeader = headers.getHeaders.getFirstValue("Content-Length")
          val acceptRanges = headers.getHeaders.getFirstValue("Accept-Ranges")
          if (contentLengthHeader != null) {
            log.debug(s"contentLength=$contentLengthHeader")
            contentLength = contentLengthHeader.toLong
          }
          if (acceptRanges != null) {
            log.debug(s"accept-ranges=$acceptRanges")
            acceptByteRange = acceptRanges.toLowerCase == "bytes"
          }
          downloaded = 0
          percentage = 0.0
        case Some(path) =>
          downloaded = new File(path).length()
          percentage = downloaded * 100.0 / totalLength
          log.debug(f"Resuming $uri%s from ${upstream.name}%s, $percentage%.2f %% already downloaded.")
      }
      context.setReceiveTimeout(connector.connectionIdleTimeout - 1.second)
  }


}


