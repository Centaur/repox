package com.gtan.repox.config

import com.gtan.repox.SerializationSupport
import com.gtan.repox.data.{ProxyServer, Repo}
import play.api.libs.json.{JsValue, Json}

trait ProxyPersister {

  case class NewOrUpdateProxy(proxy: ProxyServer) extends Cmd {
    override def transform(old: Config) = {
      val oldProxies = old.proxies
      old.copy(proxies = proxy.id.fold(oldProxies :+ proxy.copy(id = Some(ProxyServer.nextId))) { _id => oldProxies.map {
        case ProxyServer(Some(`_id`), _, _, _, _, _) => proxy
        case p => p
      }
      })
    }
  }

  object NewOrUpdateProxy {
    implicit val format = Json.format[NewOrUpdateProxy]
  }

  case class EnableProxy(id: Long) extends Cmd {
    override def transform(old: Config) = {
      val oldProxies = old.proxies
      old.copy(proxies = oldProxies.map {
        case p@ProxyServer(Some(`id`), _, _, _, _, _) => p.copy(disabled = false)
        case p => p
      })
    }
  }

  object EnableProxy {
    implicit val format = Json.format[EnableProxy]
  }

  case class DisableProxy(id: Long) extends Cmd {
    override def transform(old: Config) = {
      val oldProxies = old.proxies
      old.copy(proxies = oldProxies.map {
        case p@ProxyServer(Some(`id`), _, _, _, _, _) => p.copy(disabled = true)
        case p => p
      })
    }
  }

  object DisableProxy {
    implicit val format = Json.format[DisableProxy]
  }

  case class DeleteProxy(id: Long) extends Cmd {
    override def transform(old: Config) = {
      val oldProxies = old.proxies
      val oldProxyUsage = old.connectorUsage
      old.copy(
        proxies = oldProxies.filterNot(_.id == Some(id)),
        connectorUsage = oldProxyUsage.filterNot { case (repo, proxy) => proxy.id == Some(id)}
      )
    }
  }

  object DeleteProxy {
    implicit val format = Json.format[DeleteProxy]
  }
}

object ProxyPersister extends SerializationSupport {
  import ConfigPersister._

  val NewOrUpdateProxyClass            = classOf[NewOrUpdateProxy].getName
  val EnableProxyClass                 = classOf[EnableProxy].getName
  val DisableProxyClass                = classOf[DisableProxy].getName
  val DeleteProxyClass                 = classOf[DeleteProxy].getName

  override val reader: (JsValue) => PartialFunction[String, Cmd] = payload => {
    case NewOrUpdateProxyClass => payload.as[NewOrUpdateProxy]
    case DisableProxyClass => payload.as[DisableProxy]
    case EnableProxyClass => payload.as[EnableProxy]
    case DeleteProxyClass => payload.as[DeleteProxy]
  }

  override val writer  : PartialFunction[Cmd, JsValue]             = {
    case o: NewOrUpdateProxy => Json.toJson(o)
    case o: DisableProxy => Json.toJson(o)
    case o: EnableProxy => Json.toJson(o)
    case o: DeleteProxy => Json.toJson(o)
  }
}
