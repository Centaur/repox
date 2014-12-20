package com.gtan.repox.config

import com.gtan.repox.admin.RepoVO
import com.gtan.repox.data.Repo
import play.api.libs.json.Json

trait RepoPersister {

  case class NewRepo(vo: RepoVO) extends Cmd {
    override def transform(old: Config) = {
      val oldRepos = old.repos
      val oldConnectorUsage = old.connectorUsage
      // ToDo: validation
      val voWithId = vo.copy(repo = vo.repo.copy(id = Some(Repo.nextId)))
      val insertPoint = oldRepos.indexWhere(_.priority > vo.repo.priority)
      val newRepos = if(insertPoint == -1) { // put to the last
        old.copy(repos = oldRepos :+ voWithId.repo)
      }  else {
        val (before, after) = oldRepos.splitAt(insertPoint)
        old.copy(repos = (before :+ voWithId.repo) ++ after)
      }
      vo.connector match {
        case None => newRepos
        case Some(p) => newRepos.copy(connectorUsage = oldConnectorUsage.updated(voWithId.repo, p))
      }
    }

    override def toJson = Json.toJson(this)
  }
  object NewRepo {
    implicit val format = Json.format[NewRepo]
  }

  case class DisableRepo(id: Long) extends Cmd {
    override def transform(old: Config) = {
      val oldRepos = old.repos
      old.copy(repos = oldRepos.map {
        case o@Repo(Some(`id`), _, _, _, _, _, _) => o.copy(disabled = true)
        case o => o
      })
    }
    override def toJson = Json.toJson(this)
  }
  object DisableRepo {
    implicit val format = Json.format[DisableRepo]
  }

  case class EnableRepo(id: Long) extends Cmd {
    override def transform(old: Config) = {
      val oldRepos = old.repos
      old.copy(repos = oldRepos.map {
        case o@Repo(Some(`id`), _, _, _, _, _, _) => o.copy(disabled = false)
        case o => o
      })
    }
    override def toJson = Json.toJson(this)
  }
  object EnableRepo {
    implicit val format = Json.format[EnableRepo]
  }

  case class DeleteRepo(id: Long) extends Cmd {
    override def transform(old: Config) = {
      val oldRepos = old.repos
      val oldProxyUsage = old.connectorUsage
      old.copy(
        repos = oldRepos.filterNot(_.id == Some(id)),
        connectorUsage = oldProxyUsage.filterNot { case (repo, proxy) => repo.id == Some(id)}
      )
    }
    override def toJson = Json.toJson(this)
  }
  object DeleteRepo {
    implicit val format = Json.format[DeleteRepo]
  }

  case class UpdateRepo(vo: RepoVO) extends Cmd {
    override def transform(old: Config) = {
      val oldRepos = old.repos
      val oldConnectorUsage = old.connectorUsage

      val newConfig = for (found <- oldRepos.find(_.id == vo.repo.id)) yield {
        val indexOfTarget = oldRepos.indexOf(found)
        val repoUpdated: Config = old.copy(repos = oldRepos.updated(indexOfTarget, vo.repo))
        (oldConnectorUsage.get(vo.repo), vo.connector) match {
          case (None, None) => repoUpdated
          case (None, Some(p)) => repoUpdated.copy(connectorUsage = oldConnectorUsage.updated(vo.repo, p))
          case (Some(p), None) => repoUpdated.copy(connectorUsage = oldConnectorUsage - vo.repo)
          case (Some(o), Some(n)) if o == n => repoUpdated
          case (Some(o), Some(n)) => repoUpdated.copy(connectorUsage = oldConnectorUsage.updated(vo.repo, n))
        }
      }
      newConfig.getOrElse(old)
    }
    override def toJson = Json.toJson(this)
  }
  object UpdateRepo {
    implicit val format = Json.format[UpdateRepo]
  }

  case class MoveUpRepo(id: Long) extends Cmd {
    override def transform(old: Config) = {
      val oldRepos = old.repos
      val repo = oldRepos.find(_.id == Some(id))
      repo.fold(old) { _repo =>
        val index = oldRepos.indexOf(_repo)
        if (index == 0) {
          if (_repo.priority == 1) old // no higher level
          else old.copy(
            repos = oldRepos.map {
              case `_repo` => _repo.copy(priority = _repo.priority - 1)
              case r => r
            })
        } else {
          val previous = oldRepos(index - 1)
          if (previous.priority == _repo.priority) {
            // swap this two
            old.copy(
              repos = oldRepos.map {
                case `previous` => _repo
                case `_repo` => previous
                case r => r
              }
            )
          } else {
            // if(previous.priority == _repo.priority - 1)  uplevel as last
            // if(previous.priority < _repo.priority - 1)  uplevel as the only one
            old.copy(
              repos = oldRepos.map {
                case `_repo` => _repo.copy(priority = _repo.priority - 1)
                case r => r
              }
            )
          }
        }
      }
    }
    override def toJson = Json.toJson(this)
  }

  object MoveUpRepo {
    implicit val format = Json.format[MoveUpRepo]
  }

  case class MoveDownRepo(id: Long) extends Cmd {
    override def transform(old: Config) = {
      val oldRepos = old.repos
      val repo = oldRepos.find(_.id == Some(id))
      repo.fold(old) { _repo =>
        val index = oldRepos.indexOf(_repo)
        if (index == oldRepos.length - 1) {
          if (_repo.priority == 10) old // no lower priority
          else old.copy(
            repos = oldRepos.map {
              case `_repo` => _repo.copy(priority = _repo.priority + 1)
              case r => r
            }
          )
        } else {
          val next = oldRepos(index + 1)
          if (next.priority == _repo.priority) {
            // swap this two
            old.copy(
              repos = oldRepos.map {
                case `next` => _repo
                case `_repo` => next
                case r => r
              }
            )
          } else {
            // if(next.priority == _repo.priority + 1)  downlevel as first
            // if(next.priority > _repo.priority + 1)  downlevel as the only one
            old.copy(
              repos = oldRepos.map {
                case `_repo` => _repo.copy(priority = _repo.priority + 1)
                case r => r
              }
            )
          }
        }
      }
    }
    override def toJson = Json.toJson(this)
  }
  object MoveDownRepo {
    implicit val format = Json.format[MoveDownRepo]
  }

}