package com.gtan.repox.config

import com.google.gson
import com.google.gson.Gson
import com.gtan.repox._
import com.ning.http.client.{ProxyServer => JProxyServer}
import com.ning.http.client.ProxyServer.Protocol

import scala.language.postfixOps

/**
 * Created by xf on 14/12/4.
 */
trait Jsonable[T] {
  def toJson(v: T): String
  def fromJson(json: String): T
}

object Jsonable {

  import collection.JavaConverters._

  val gson = new Gson

  implicit object repoIsJsonable extends Jsonable[Repo] {
    override def toJson(repo: Repo): String = gson.toJson(
      Map("name" -> repo.name, "base" -> repo.base, "priority" -> repo.priority, "getOnly" -> repo.getOnly, "maven" -> repo.maven).asJava
    )

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

  implicit def seqIsJsonable[T: Jsonable]: Jsonable[Seq[T]] = new Jsonable[Seq[T]] {
    override def toJson(xs: Seq[T]): String = gson.toJson(xs.map(implicitly[Jsonable[T]].toJson))

    override def fromJson(json: String): Seq[T] = {
      val v = gson.fromJson(json, classOf[java.util.Vector[String]]).asScala
      v.map(implicitly[Jsonable[T]].fromJson).toVector
    }
  }

  implicit def mapIsJsonable[T: Jsonable]: Jsonable[Map[String, T]] = new Jsonable[Map[String, T]] {
    override def toJson(m: Map[String, T]): String = gson.toJson(m.map {
      case (k, v) => k -> implicitly[Jsonable[T]].toJson(v)
    } asJava)

    override def fromJson(json: String): Map[String, T] = {
      val map = gson.fromJson(json, classOf[java.util.Map[String, String]]).asScala.toMap
      for((k, v) <- map) yield k -> implicitly[Jsonable[T]].fromJson(v)
    }
  }

  implicit object repoVOIsJsonable extends Jsonable[RepoVO] {
    override def toJson(vo: RepoVO): String = {
      val map = Map("name" -> vo.repo.name, "base" -> vo.repo.base, "priority" -> vo.repo.priority, "getOnly" -> vo.repo.getOnly,
        "maven" -> vo.repo.maven)
      gson.toJson(
        (vo.proxy match {
          case None => map
          case Some(p) => map.updated("admin/proxy", implicitly[Jsonable[JProxyServer]].toJson(p))
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
        id = if(map.containsKey("id")) map.get("id").toLong else -1,
        include = map.get("include"),
        exclude = map.asScala.get("exclude")
      )
    }
  }

}
