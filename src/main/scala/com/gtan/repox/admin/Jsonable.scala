package com.gtan.repox.admin

import com.google.gson.Gson
import com.gtan.repox._
import com.ning.http.client.ProxyServer.Protocol
import com.ning.http.client.{ProxyServer => JProxyServer}

import scala.language.postfixOps

/**
 * Created by xf on 14/12/4.
 */
trait Jsonable[T] {
  def toJson(v: T): String

  def fromJson(json: String): T
}

object Jsonable {

  import scala.collection.JavaConverters._

  val gson = new Gson

  implicit object repoIsJsonable extends Jsonable[Repo] {
    override def toJson(repo: Repo): String = {
      gson.toJson(repo.toMap.asJava)
    }

    override def fromJson(json: String): Repo = {
      val map = gson.fromJson(json, classOf[java.util.Map[String, String]])
      Repo(map)
    }
  }

  implicit object ProxyServerIsJsonable extends Jsonable[JProxyServer] {
    override def toJson(v: JProxyServer): String = gson.toJson(
      Map("type" -> v.getProtocolAsString, "host" -> v.getHost, "port" -> v.getPort)
    )

    override def fromJson(json: String): JProxyServer = {
      val map = gson.fromJson(json, classOf[java.util.Map[String, String]])
      new JProxyServer(Protocol.valueOf(map.get("protocol")), map.get("host"), map.get("port").toInt)
    }
  }

  implicit def jListIsJsonable[T: Jsonable]: Jsonable[java.util.List[T]] = new Jsonable[java.util.List[T]] {
    override def toJson(xs: java.util.List[T]): String = gson.toJson(xs.asScala.map(implicitly[Jsonable[T]].toJson).asJava)

    override def fromJson(json: String): java.util.List[T] = {
      val v = gson.fromJson(json, classOf[java.util.List[String]]).asScala
      v.map(implicitly[Jsonable[T]].fromJson).asJava
    }
  }

  implicit def mapIsJsonable[T: Jsonable]: Jsonable[java.util.Map[String, T]] = new Jsonable[java.util.Map[String, T]] {
    override def toJson(m: java.util.Map[String, T]): String = gson.toJson(m.asScala.map {
      case (k, v) => k -> implicitly[Jsonable[T]].toJson(v)
    } asJava)

    override def fromJson(json: String): java.util.Map[String, T] = {
      val map = gson.fromJson(json, classOf[java.util.Map[String, String]]).asScala
      (for ((k, v) <- map) yield k -> implicitly[Jsonable[T]].fromJson(v)).asJava
    }
  }

  implicit object repoVOIsJsonable extends Jsonable[RepoVO] {
    override def toJson(vo: RepoVO): String = {
      val map = vo.repo.toMap
      gson.toJson(
        (vo.proxy match {
          case None => map
          case Some(p) => map.updated("proxy", implicitly[Jsonable[JProxyServer]].toJson(p))
        }).asJava
      )
    }

    override def fromJson(json: String): RepoVO = {
      val map = gson.fromJson(json, classOf[java.util.Map[String, String]])
      RepoVO(
        repo = Repo(map),
        proxy = map.asScala.get("admin/proxy").map(ProxyServer.apply)
      )
    }
  }

  implicit object immediate404RuleIsJsonable extends Jsonable[Immediate404Rule] {
    override def toJson(v: Immediate404Rule): String = gson.toJson(
      (v.exclude match {
        case None => Map("include" -> v.include)
        case Some(ex) => Map("include" -> v.include, "exclude" -> ex)
      }) asJava
    )

    override def fromJson(json: String): Immediate404Rule = {
      val map = gson.fromJson(json, classOf[java.util.Map[String, String]])
      Immediate404Rule(
        id = if (map.containsKey("id")) map.get("id").toLong else -1,
        include = map.get("include"),
        exclude = map.asScala.get("exclude")
      )
    }
  }

}
