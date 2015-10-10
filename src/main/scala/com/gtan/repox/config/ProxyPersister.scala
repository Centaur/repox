package com.gtan.repox.config

import com.gtan.repox.SerializationSupport
import com.gtan.repox.data.{Connector, ProxyServer, Repo}
import play.api.libs.json.{JsValue, Json}

object ProxyPersister extends SerializationSupport {

  case class NewOrUpdateProxy(proxy: ProxyServer) extends ConfigCmd {
    override def transform(old: Config) = {
      val oldProxies = old.proxies
      val oldProxyUsages: Map[Connector, ProxyServer] = old.proxyUsage
      old.copy(proxies = proxy.id.fold(oldProxies :+ proxy.copy(id = Some(ProxyServer.nextId.incrementAndGet()))) { _id =>
        oldProxies.map {
          case ProxyServer(Some(`_id`), _, _, _, _, _) => proxy
          case p => p
        }
      }, proxyUsage = proxy.id.fold(oldProxyUsages) { _id =>
        oldProxyUsages.map {
          case (connector, ProxyServer(Some(`_id`), _, _, _, _, _)) => connector -> proxy
          case u => u
        }
      })
    }
  }

  implicit val newOrUpdateProxyformat = Json.format[NewOrUpdateProxy]

  case class EnableProxy(id: Long) extends ConfigCmd {
    override def transform(old: Config) = {
      old.copy(proxies = old.proxies.map {
        case p@ProxyServer(Some(`id`), _, _, _, _, _) => p.copy(disabled = false)
        case p => p
      })
    }
  }

  implicit val enableProxyFormat = Json.format[EnableProxy]

  case class DisableProxy(id: Long) extends ConfigCmd {
    override def transform(old: Config) = {
      old.copy(proxies = old.proxies.map {
        case p@ProxyServer(Some(`id`), _, _, _, _, _) => p.copy(disabled = true)
        case p => p
      }, proxyUsage = old.proxyUsage.filterNot {
        case (connector, proxy) => proxy.id.contains(id)
      })
    }
  }

  implicit val disableProxyFormat = Json.format[DisableProxy]

  case class DeleteProxy(id: Long) extends ConfigCmd {
    override def transform(old: Config) = {
      old.copy(
        proxies = old.proxies.filterNot(_.id.contains(id)),
        proxyUsage = old.proxyUsage.filterNot { case (connector, proxy) => proxy.id.contains(id) }
      )
    }
  }

  implicit val deleteProxyFormat = Json.format[DeleteProxy]

  val NewOrUpdateProxyClass = classOf[NewOrUpdateProxy].getName
  val EnableProxyClass = classOf[EnableProxy].getName
  val DisableProxyClass = classOf[DisableProxy].getName
  val DeleteProxyClass = classOf[DeleteProxy].getName

  override val reader: (JsValue) => PartialFunction[String, Jsonable] = payload => {
    case NewOrUpdateProxyClass => payload.as[NewOrUpdateProxy]
    case DisableProxyClass => payload.as[DisableProxy]
    case EnableProxyClass => payload.as[EnableProxy]
    case DeleteProxyClass => payload.as[DeleteProxy]
  }

  override val writer: PartialFunction[Jsonable, JsValue] = {
    case o: NewOrUpdateProxy => Json.toJson(o)
    case o: DisableProxy => Json.toJson(o)
    case o: EnableProxy => Json.toJson(o)
    case o: DeleteProxy => Json.toJson(o)
  }
}
