package com.gtan.repox

import java.io.File
import java.nio.file.{Files, Path, Paths}

import akka.actor.ActorRef
import com.gtan.repox.data.Repo
import com.typesafe.scalalogging.LazyLogging
import fs2.Task
import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.resource.{ResourceHandler, ResourceManager}
import io.undertow.util.{Headers, HttpString, MimeMappings, StatusCodes}
import org.http4s._
import org.http4s.dsl.{uri => _, _}
import org.http4s.headers._

trait HttpHelpers {
  self: LazyLogging =>
  type StatusCode = Int
  type ResponseHeaders = Map[String, java.util.List[String]]
  type HeaderResponse = (Repo, StatusCode, ResponseHeaders)

  def respond404(exchange: HttpServerExchange): Unit = {
    exchange.setStatusCode(StatusCodes.NOT_FOUND)
    exchange.getResponseChannel // just to avoid mysterious setting Content-length to 0 in endExchange, ugly
    exchange.endExchange()
  }

  def immediate404(exchange: HttpServerExchange): Unit = {
    logger.info(s"Immediate 404 ${exchange.getRequestURI}.")
    respond404(exchange)
  }

  def immediate4044s(sender: ActorRef, uri: String): Unit = {
    logger.info(s"Immediate 404 uri.")
    sender ! NotFound()
  }

  def smart404(exchange: HttpServerExchange): Unit = {
    logger.info(s"Smart 404 ${exchange.getRequestURI}.")
    respond404(exchange)
  }

  def smart4044s(sender: ActorRef, u: String): Unit = {
    logger.info(s"Smart 404 $u.")
    sender ! NotFound()
  }

  def sendFile(resourceHandler: ResourceHandler, exchange: HttpServerExchange): Unit = {
    resourceHandler.handleRequest(exchange)
  }

  def sendFile4s(sender: ActorRef, request: org.http4s.Request, root: Path): Unit = {
    sender ! StaticFile.fromFile(root.resolve(request.uri.toString).toFile, Some(request))
  }

  def immediateFile(resourceHandler: ResourceHandler, exchange: HttpServerExchange): Unit = {
    logger.debug(s"Immediate file ${exchange.getRequestURI}")
    sendFile(resourceHandler, exchange)
  }

  def immediateFile4s(sender: ActorRef, request: org.http4s.Request, file: File): Unit = {
    logger.debug(s"Immediate file ${request.uri}")
//    sender ! StaticFile.fromFile(file, Some(request)).map(Task.now).fold(NotFound())(identity)
  }

  def respondHead(exchange: HttpServerExchange, headers: ResponseHeaders): Unit = {
    exchange.setStatusCode(200)
    val target = exchange.getResponseHeaders
    for ((k, v) <- headers)
      target.putAll(new HttpString(k), v)
    exchange.getResponseChannel // just to avoid mysterious setting Content-length to 0 in endExchange, ugly
    exchange.endExchange()
  }

  def immediateHead(resourceManager: ResourceManager, exchange: HttpServerExchange): Unit = {
    val uri = exchange.getRequestURI
    val resource = resourceManager.getResource(uri)
    exchange.setStatusCode(200)
    val headers = exchange.getResponseHeaders
    headers.put(Headers.CONTENT_LENGTH, resource.getContentLength)
      .put(Headers.SERVER, "repox")
      .put(Headers.CONNECTION, Headers.KEEP_ALIVE.toString)
      .put(Headers.CONTENT_TYPE, resource.getContentType(MimeMappings.DEFAULT))
      .put(Headers.LAST_MODIFIED, resource.getLastModifiedString)
    exchange.getResponseChannel // just to avoid mysterious setting Content-length to 0 in endExchange, ugly
    exchange.endExchange()
    logger.debug(s"Immediate head $uri. ")
  }

  def immediateHead4s(sender: ActorRef, file: File, request: org.http4s.Request): Unit = {
    import org.http4s.syntax.string._
    val uri = request.uri.toString()
    sender ! Ok().putHeaders(
      `Content-Length`.unsafeFromLong(file.length()),
      Header("Server", "repox"),
      Connection("keep-alive".ci),
      `Content-Type`(MediaType.forExtension(uri.drop(uri.lastIndexOf('.') + 1))
        .getOrElse(MediaType.`application/octet-stream`)),
      Header("Last-Modified",
        HttpDate
          .fromInstant(Files.getLastModifiedTime(file.toPath).toInstant)
          .toString)
    )
    logger.debug(s"Immediate head $uri. ")
  }

  def redirectTo(exchange: HttpServerExchange, targetLocation: String): Unit = {
    exchange.setStatusCode(StatusCodes.TEMPORARY_REDIRECT)
    exchange.getResponseHeaders.put(Headers.LOCATION, targetLocation)
    exchange.endExchange()
  }
}
