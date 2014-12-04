package com.gtan.repox.admin

import com.gtan.repox.Repo
import com.gtan.repox.config.Config
import io.undertow.server.HttpServerExchange
import io.undertow.util.{Methods, HttpString}
import collection.JavaConverters._

object UpstreamsHandler extends RestHandler{

  import WebConfigHandler._

  override def route(implicit exchange: HttpServerExchange): PartialFunction[(HttpString, String), Unit] = {
    case (Methods.GET, "upstreams") =>
      respondJson(exchange, Config.repos.sortBy(_.id).map(RepoVO.apply).asJava)
    case (Methods.POST, "upstream") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
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
      setConfigAndRespond(exchange, newConfig)
    case (Methods.PUT, "upstream") =>
      val newV = exchange.getQueryParameters.get("v").getFirst
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
        setConfigAndRespond(exchange, newConfig)
      }

  }
}
