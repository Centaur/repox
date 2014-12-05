package com.gtan.repox.config

import com.gtan.repox.Repo
import com.gtan.repox.admin.{Jsonable, RepoVO}

trait RepoPersister {

  case class NewRepo(vo: RepoVO) extends Cmd {
    override def transform(old: Config) = {
      val oldRepos = old.repos
      val oldProxyUsage = old.proxyUsage
      // ToDo: validation
      val voWithId = vo.copy(repo = vo.repo.copy(id = Repo.nextId))
      val newRepos = old.copy(repos = oldRepos :+ voWithId.repo)
      vo.proxy match {
        case None => newRepos
        case Some(p) => newRepos.copy(proxyUsage = oldProxyUsage.updated(voWithId.repo, p))
      }
    }
  }

  case class DeleteRepo(vo: RepoVO) extends Cmd {
    override def transform(old: Config) = {
      val oldRepos = old.repos
      val oldProxyUsage = old.proxyUsage
      old.copy(
        repos = oldRepos.filterNot(_ == vo.repo),
        proxyUsage = oldProxyUsage - vo.repo
      )
    }
  }

  case class UpdateRepo(vo: RepoVO) extends Cmd {
    override def transform(old: Config) = {
      val oldRepos = old.repos
      val oldProxyUsage = old.proxyUsage

      val newConfig = for (found <- oldRepos.find(_.id == vo.repo.id)) yield {
        val indexOfTarget = oldRepos.indexOf(found)
        val repoUpdated: Config = old.copy(repos = oldRepos.updated(indexOfTarget, vo.repo))
        (oldProxyUsage.get(vo.repo), vo.proxy) match {
          case (None, None) => repoUpdated
          case (None, Some(p)) => repoUpdated.copy(proxyUsage = oldProxyUsage.updated(vo.repo, p))
          case (Some(p), None) => repoUpdated.copy(proxyUsage = oldProxyUsage - vo.repo)
          case (Some(o), Some(n)) if o == n => repoUpdated
          case (Some(o), Some(n)) => repoUpdated.copy(proxyUsage = oldProxyUsage.updated(vo.repo, n))
        }
      }
      newConfig.getOrElse(old)
    }
  }

}