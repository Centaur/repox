package com.gtan.repox

import java.io.File
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.{FileLock, CompletionHandler, AsynchronousFileChannel}
import java.nio.file.{StandardCopyOption, Files, Paths, Path}
import java.util.concurrent

import com.ning.http.client.AsyncHandler.STATE
import com.ning.http.client._
import com.typesafe.scalalogging.{LazyLogging, Logger}
import io.undertow.Handlers
import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.resource.FileResourceManager
import io.undertow.util.HttpString

import scala.collection.JavaConverters._
import scala.concurrent.{Future, Promise}
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
 * Created by xf on 14/11/20.
 */
object Repox extends LazyLogging {

  import scala.concurrent.ExecutionContext.Implicits.global

  val storage = Paths.get(System.getProperty("user.home"), ".repox", "storage")

  val upstreams = List(
    "http://uk.maven.org/maven2",
    "http://maven.oschina.net/content/groups/public",
    "http://repo1.maven.org/maven2",
    "https://dl.bintray.com/sbt/sbt-plugin-releases"
  )
  val client = new AsyncHttpClient()

  type StatusCode = Int
  type ResponseHeaders = Map[String, java.util.List[String]]
  type Upstream = String
  type HeaderResponse = (Upstream, StatusCode, ResponseHeaders)

  def tryGet(exchange: HttpServerExchange, upstream: String): Promise[(Upstream, Promise[Path])] = {
    val promise = Promise[(Upstream, Promise[Path])]()
    var statusCode = 200
    var tempFileChannel: AsynchronousFileChannel = null
    var tempFile: File = null
    var pathPromise: Promise[Path] = null

    val requestHeaders = new FluentCaseInsensitiveStringsMap()
    for (name <- exchange.getRequestHeaders.getHeaderNames.asScala) {
      requestHeaders.add(name.toString, exchange.getRequestHeaders.get(name))
    }
    val upstreamUrl = upstream + exchange.getRequestURI
    val upstreamHost = new URL(upstreamUrl).getHost
    requestHeaders.put("Host", List(upstreamHost).asJava)

    client.prepareGet(upstreamUrl)
      .setHeaders(requestHeaders)
      .execute(new AsyncHandler[Unit] {
      def clearTempFile(): Unit = {
        if (tempFileChannel.isOpen)
          tempFileChannel.close()
        //        if (tempFile != null)
        //          tempFile.delete()
      }

      override def onThrowable(t: Throwable): Unit = promise.failure(t)

      override def onCompleted(): Unit = {
        if (tempFileChannel.isOpen)
          tempFileChannel.close()
        logger.debug(s"get file from $upstreamUrl completed. Completing pathPromise.")
        pathPromise.success(tempFile.toPath)
      }

      override def onBodyPartReceived(bodyPart: HttpResponseBodyPart): STATE = {
        logger.debug(s"onBodyPartReceived, pathPromise.isCompleted = ${pathPromise.isCompleted}")
        if (pathPromise != null && !pathPromise.isCompleted) {
          val buffer = bodyPart.getBodyByteBuffer
          tempFileChannel.write(buffer, tempFileChannel.size())
          //          exchange.getResponseChannel.write(buffer)
          STATE.CONTINUE
        } else {
          // some other upstream got chosen
          if (tempFileChannel.isOpen)
            tempFileChannel.close()
          if (tempFile != null) {
            logger.debug(s"deleting ${tempFile.getAbsolutePath}")
            tempFile.delete()
          }
          STATE.ABORT
        }
      }

      override def onStatusReceived(responseStatus: HttpResponseStatus): STATE = {
        if (!promise.isCompleted) {
          statusCode = responseStatus.getStatusCode
          logger.debug(s"statusCode from $upstreamUrl: $statusCode")
          if (statusCode != 200) {
            promise.failure(UnsuccessResponseStatus(responseStatus))
            STATE.ABORT
          } else
            STATE.CONTINUE
        } else {
          // some other upstream got chosen
          STATE.ABORT
        }
      }

      override def onHeadersReceived(headers: HttpResponseHeaders): STATE = {
        if (!promise.isCompleted) {
          logger.debug(s"Got headers From $upstreamUrl")
          logger.debug(s"headers ================== \n ${headers.getHeaders}")
          pathPromise = Promise[Path]()
          promise.success(upstream -> pathPromise)
          tempFile = File.createTempFile("repox", ".tmp")
          logger.debug(s"getting from $upstreamUrl to ${tempFile.getPath}")
          tempFileChannel = AsynchronousFileChannel.open(tempFile.toPath)
          STATE.CONTINUE
        } else {
          // some other upstream got chosen
          STATE.ABORT
        }
      }
    })

    promise
  }

  def tryHead(exchange: HttpServerExchange, upstream: String): Future[HeaderResponse] = {
    val upstreamUrl = upstream + exchange.getRequestURI
    val promise = Promise[HeaderResponse]()
    var statusCode = 200

    val requestHeaders = new FluentCaseInsensitiveStringsMap()
    for (name <- exchange.getRequestHeaders.getHeaderNames.asScala) {
      requestHeaders.add(name.toString, exchange.getRequestHeaders.get(name))
    }
    val upstreamHost = new URL(upstreamUrl).getHost
    requestHeaders.put("Host", List(upstreamHost).asJava)

    client.prepareHead(upstreamUrl)
      .setHeaders(requestHeaders)
      .execute(new AsyncHandler[Unit] {
      override def onThrowable(t: Throwable): Unit = promise.failure(t)

      override def onCompleted(): Unit = {
        /* we don't get here actually */
      }

      override def onBodyPartReceived(bodyPart: HttpResponseBodyPart): STATE = {
        // we don't get here actually
        STATE.CONTINUE
      }

      override def onStatusReceived(responseStatus: HttpResponseStatus): STATE = {
        if (!promise.isCompleted) {
          statusCode = responseStatus.getStatusCode
          logger.debug(s"statusCode from $upstreamUrl: $statusCode")
          STATE.CONTINUE
        } else STATE.ABORT
      }

      override def onHeadersReceived(headers: HttpResponseHeaders): STATE = {
        if (!promise.isCompleted) {
          import scala.collection.JavaConverters._
          logger.debug(s"Got headers From $upstreamUrl")
          logger.debug(s"headers ================== \n ${headers.getHeaders}")
          promise.success(upstream, statusCode, mapAsScalaMapConverter(headers.getHeaders).asScala.toMap)
        }
        STATE.ABORT
      }
    })

    import scala.concurrent.duration._
    TimeoutableFuture(promise, after = 10 seconds, name = upstreamHost)
  }

  def handle(exchange: HttpServerExchange): Unit = {

    exchange.getRequestMethod.toString.toUpperCase match {
      case "HEAD" =>
        val firstSuccess = Future.find(upstreams.map(upstream => tryHead(exchange, upstream))) {
          case (_, 200, _) => true
          case _ => false
        }
        firstSuccess.onComplete {
          case Success(Some(Tuple3(upstream, statusCode, headers))) =>
            logger.debug(s"statusCode=$statusCode, headers=$headers")
            exchange.setResponseCode(204) // no content in body
            for ((k, v) <- headers) {
              exchange.getResponseHeaders.addAll(new HttpString(k), v.asScala.distinct.asJava)
            }
            exchange.getResponseHeaders.add(new HttpString("Data-Source"), upstream)
            exchange.getResponseChannel // just to avoid mysterious setting Content-length to 0 in endExchange, ugly
            exchange.endExchange()
          case Success(None) | Failure(_) =>
            exchange.setResponseCode(404)
            exchange.endExchange()
        }
      case "GET" =>
        val resolvedPath = storage.resolve(exchange.getRequestURI.tail)
        if (resolvedPath.toFile.exists()) {
          logger.debug("Already downloaded. Serve immediately.")
          Handlers.resource(new FileResourceManager(storage.toFile, 100 * 1024)).handleRequest(exchange)
        } else {
          logger.debug("Start download....")

          val promises = upstreams.map(upstream => tryGet(exchange, upstream))
          for (future <- promises.map(_.future)) {
            future.onSuccess {
              case (upstream, _) =>
                for (promise <- promises if promise.future != future) {
                  if(!promise.isCompleted) promise.failure(PeerChosen(upstream))
                  logger.debug(s"chosen upstream $upstream")
                }
            }
          }
          val firstGotHeaders = Future.find(promises.map(_.future))(_ => true)
          firstGotHeaders.onComplete {
            case Success(Some((upstream, promise))) =>
              promise.future.onComplete {
                case Success(path) =>
                  resolvedPath.getParent.toFile.mkdirs()
                  Files.move(path, resolvedPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                  Files.delete(path)
                  Handlers.resource(new FileResourceManager(storage.toFile, 100 * 1024)).handleRequest(exchange)
                case Failure(e) =>
                  exchange.setResponseCode(404)
                  exchange.endExchange()
              }
            case Success(None) | Failure(_) =>
              exchange.setResponseCode(404)
              exchange.endExchange()
          }
        }
    }
  }
}
