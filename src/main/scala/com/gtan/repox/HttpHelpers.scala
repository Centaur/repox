package com.gtan.repox

import com.gtan.repox.data.Repo
import com.typesafe.scalalogging.LazyLogging
import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.resource.{ResourceManager, ResourceHandler}
import io.undertow.util.{MimeMappings, Headers, HttpString, StatusCodes}

trait HttpHelpers { self: LazyLogging =>
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

  def smart404(exchange: HttpServerExchange): Unit = {
    logger.info(s"Smart 404 ${exchange.getRequestURI}.")
    respond404(exchange)
  }

  def sendFile(resourceHandler: ResourceHandler, exchange: HttpServerExchange): Unit = {
    resourceHandler.handleRequest(exchange)
  }

  def immediateFile(resourceHandler: ResourceHandler, exchange: HttpServerExchange): Unit = {
    logger.debug(s"Immediate file ${exchange.getRequestURI}")
    sendFile(resourceHandler, exchange)
  }

  def respondHead(exchange: HttpServerExchange, headers: ResponseHeaders): Unit = {
    exchange.setStatusCode(200)
    val target = exchange.getResponseHeaders
    for ((k, v) <- headers)
      target.putAll(new HttpString(k), v)
    exchange.getResponseChannel // just to avoid mysterious setting Content-length to 0 in endExchange, ugly
    exchange.setStatusCode(200)
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

  def redirectTo(exchange: HttpServerExchange, targetLocation: String): Unit = {
    exchange.setStatusCode(StatusCodes.TEMPORARY_REDIRECT)
    exchange.getResponseHeaders.put(Headers.LOCATION, targetLocation)
    exchange.endExchange()
  }
}
