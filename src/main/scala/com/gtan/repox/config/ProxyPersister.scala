package com.gtan.repox.config

import com.gtan.repox.SerializationSupport
import com.gtan.repox.data.{ProxyServer, Repo}
import play.api.libs.json.{JsValue, Json}

object ProxyPersister extends SerializationSupport {

  // ToDo: need to update ProxyUsage when update Proxy. Separate New and Update
  case class NewOrUpdateProxy(proxy: ProxyServer) extends Cmd {
    override def transform(old: Config) = {
      val oldProxies = old.proxies
      old.copy(proxies = proxy.id.fold(oldProxies :+ proxy.copy(id = Some(ProxyServer.nextId.incrementAndGet()))) { _id =>
        oldProxies.map {
          case ProxyServer(Some(`_id`), _, _, _, _, _) => proxy
          case p => p
        }
      })
    }
  }

  implicit val newOrUpdateProxyformat = Json.format[NewOrUpdateProxy]

  case class EnableProxy(id: Long) extends Cmd {
    override def transform(old: Config) = {
      val oldProxies = old.proxies
      old.copy(proxies = oldProxies.map {
        case p@ProxyServer(Some(`id`), _, _, _, _, _) => p.copy(disabled = false)
        case p => p
      })
    }
  }

  implicit val enableProxyFormat = Json.format[EnableProxy]

  case class DisableProxy(id: Long) extends Cmd {
    override def transform(old: Config) = {
      val oldProxies = old.proxies
      old.copy(proxies = oldProxies.map {
        case p@ProxyServer(Some(`id`), _, _, _, _, _) => p.copy(disabled = true)
        case p => p
      })
    }
  }

  implicit val disableProxyFormat = Json.format[DisableProxy]

  case class DeleteProxy(id: Long) extends Cmd {
    override def transform(old: Config) = {
      val oldProxies = old.proxies
      val oldProxyUsage = old.connectorUsage
      old.copy(
        proxies = oldProxies.filterNot(_.id.contains(id)),
        connectorUsage = oldProxyUsage.filterNot { case (repo, proxy) => proxy.id.contains(id) }
      )
    }
  }

  implicit val deleteProxyFormat = Json.format[DeleteProxy]

  val NewOrUpdateProxyClass = classOf[NewOrUpdateProxy].getName
  val EnableProxyClass = classOf[EnableProxy].getName
  val DisableProxyClass = classOf[DisableProxy].getName
  val DeleteProxyClass = classOf[DeleteProxy].getName

  override val reader: (JsValue) => PartialFunction[String, Cmd] = payload => {
    case NewOrUpdateProxyClass => payload.as[NewOrUpdateProxy]
    case DisableProxyClass => payload.as[DisableProxy]
    case EnableProxyClass => payload.as[EnableProxy]
    case DeleteProxyClass => payload.as[DeleteProxy]
  }

  override val writer: PartialFunction[Cmd, JsValue] = {
    case o: NewOrUpdateProxy => Json.toJson(o)
    case o: DisableProxy => Json.toJson(o)
    case o: EnableProxy => Json.toJson(o)
    case o: DeleteProxy => Json.toJson(o)
  }
}
