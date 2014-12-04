package com.gtan.repox.config

import java.nio.ByteBuffer

import com.google.common.base.Charsets
import com.gtan.repox.Repo
import io.undertow.Handlers
import io.undertow.server.handlers.resource.ClassPathResourceManager
import io.undertow.server.{HttpHandler, HttpServerExchange}
import io.undertow.util.{Headers, Methods, StatusCodes}

import scala.concurrent.duration._
import scala.language.postfixOps

class WebConfigHandler extends HttpHandler {

  /**
   * intentional avoid reading request body, all data enclosed in querystring
   * @param httpServerExchange core data/state carrier
   */
  override def handleRequest(httpServerExchange: HttpServerExchange) = {
    val (method, uriUnprefixed) = (httpServerExchange.getRequestMethod, httpServerExchange.getRequestURI.drop("/admin/".length))
    (method, uriUnprefixed) match {
      case (Methods.GET, target) if isStaticRequest(target) =>
        Handlers
          .resource(new ClassPathResourceManager(this.getClass.getClassLoader))
          .handleRequest(httpServerExchange)
      case (Methods.GET, "upstreams") =>
        respondJson(httpServerExchange, Config.repos.sortBy(_.id).map(RepoVO.apply))
      case (Methods.POST, "upstream") =>
        val newV = httpServerExchange.getQueryParameters.get("v").getFirst
        val oldRepos = Config.get.repos
        val oldProxyUsage = Config.get.proxyUsage
        // ToDo: validation
        val vo = Jsonable.repoVOIsJsonable.fromJson(newV)
        val voWithId = vo.copy(repo = vo.repo.copy(id = Repo.nextId))
        val newRepos = Config.get.copy(repos = oldRepos :+ voWithId.repo)
        val newConfig = vo.proxy match {
          case None => newRepos
          case Some(p) => newRepos.copy(proxyUsage = oldProxyUsage.updated(voWithId.repo, p))
        }
        Config.set(newConfig)
        respondEmptyOK(httpServerExchange)
      case (Methods.PUT, "upstream") =>
        val newV = httpServerExchange.getQueryParameters.get("v").getFirst
        val oldRepos = Config.get.repos
        val oldProxyUsage = Config.get.proxyUsage
        val vo = Jsonable.repoVOIsJsonable.fromJson(newV)
        for (found <- oldRepos.find(_.id == vo.repo.id)) {
          val indexOfTarget = oldRepos.indexOf(found)
          val repoUpdated: Config = Config.get.copy(repos = oldRepos.updated(indexOfTarget, vo.repo))
          val newConfig = (oldProxyUsage.get(vo.repo), vo.proxy) match {
            case (None, None) => repoUpdated
            case (None, Some(p)) => repoUpdated.copy(proxyUsage = oldProxyUsage.updated(vo.repo, p))
            case (Some(p), None) => repoUpdated.copy(proxyUsage = oldProxyUsage - vo.repo)
            case (Some(o), Some(n)) => if (o.getHost.equals(n.getHost) && o.getProtocol.equals(n.getProtocol) && o.getPort == n.getPort) {
              repoUpdated
            } else {
              repoUpdated.copy(proxyUsage = oldProxyUsage.updated(vo.repo, n))
            }
          }
          Config.set(newConfig)
          respondEmptyOK(httpServerExchange)
        }
      case (Methods.GET, "proxies") =>
        respondJson(httpServerExchange, Config.proxies)
      case (Methods.POST, "proxy") =>
        val newV = httpServerExchange.getQueryParameters.get("v").getFirst
      case (Methods.PUT, "proxy") =>
        val newV = httpServerExchange.getQueryParameters.get("v").getFirst
      case (Methods.GET, "immediate404Rules") =>
        respondJson(httpServerExchange, Config.immediate404Rules)
      case (Methods.POST, "immediate404Rule") =>
        val newV = httpServerExchange.getQueryParameters.get("v").getFirst
      case (Methods.PUT, "immediate404Rule") =>
        val newV = httpServerExchange.getQueryParameters.get("v").getFirst
      case (Methods.GET, "expireRules") =>
      //        respondJson(httpServerExchange, Config.expireRules)
      case (Methods.POST, "expireRule") =>
      case (Methods.PUT, "expireRule") =>
      case (Methods.PUT, "connectionTimeout") =>
        val newV = httpServerExchange.getQueryParameters.get("v").getFirst
        Config.set(Config.get.copy(connectionTimeout = Duration.apply(newV.toLong, MILLISECONDS)))
        respondEmptyOK(httpServerExchange)
      case (Methods.PUT, "connectionIdleTimeout") =>
        val newV = httpServerExchange.getQueryParameters.get("v").getFirst
        Config.set(Config.get.copy(connectionIdleTimeout = Duration.apply(newV.toLong, MILLISECONDS)))
        respondEmptyOK(httpServerExchange)
      case (Methods.PUT, "mainClientMaxConnections") =>
        val newV = httpServerExchange.getQueryParameters.get("v").getFirst
        Config.set(Config.get.copy(mainClientMaxConnections = newV.toInt))
        respondEmptyOK(httpServerExchange)
      case (Methods.PUT, "mainClientMaxConnectionsPerHost") =>
        val newV = httpServerExchange.getQueryParameters.get("v").getFirst
        Config.set(Config.get.copy(mainClientMaxConnectionsPerHost = newV.toInt))
        respondEmptyOK(httpServerExchange)
      case (Methods.PUT, "proxyClientMaxConnections") =>
        val newV = httpServerExchange.getQueryParameters.get("v").getFirst
        Config.set(Config.get.copy(proxyClientMaxConnections = newV.toInt))
        respondEmptyOK(httpServerExchange)
      case (Methods.PUT, "proxyClientMaxConnectionsPerHost") =>
        val newV = httpServerExchange.getQueryParameters.get("v").getFirst
        Config.set(Config.get.copy(proxyClientMaxConnectionsPerHost = newV.toInt))
        respondEmptyOK(httpServerExchange)
    }

  }

  def isStaticRequest(target: String) = Set(".html", ".css", ".js", ".ico", ".ttf", ".map", "woff").exists(target.endsWith)

  def respondJson[T: Jsonable](exchange: HttpServerExchange, data: T): Unit = {
    exchange.setResponseCode(StatusCodes.OK)
    val respondHeaders = exchange.getResponseHeaders
    respondHeaders.put(Headers.CONTENT_TYPE, "application/json")
    val json = implicitly[Jsonable[T]].toJson(data)
    exchange.getResponseChannel.writeFinal(ByteBuffer.wrap(json.getBytes(Charsets.UTF_8)))
    exchange.endExchange()
  }

  def respondEmptyOK(exchange: HttpServerExchange): Unit = {
    exchange.setResponseCode(StatusCodes.OK)
    exchange.getResponseChannel
    exchange.endExchange()
  }
}
