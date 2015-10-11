package com.gtan.repox

import java.io.NotSerializableException

import akka.serialization.Serializer
import com.gtan.repox.ExpirationManager.ExpirationSeq
import com.gtan.repox.config._
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json._

trait SerializationSupport {
  val reader: JsValue => PartialFunction[String, Jsonable]
  val writer: PartialFunction[Jsonable, JsValue]
}

class JsonSerializer extends Serializer with LazyLogging with SerializationSupport {
  val ConfigChangedClass = classOf[ConfigChanged].getName

  val serializationSupports: Seq[_ <: SerializationSupport] = Seq(RepoPersister, ProxyPersister, ParameterPersister, Immediate404RulePersister, ExpireRulePersister, ConnectorPersister, ExpirationManager, ConfigPersister)

  override val reader: JsValue => PartialFunction[String, Jsonable] = { jsValue =>
    serializationSupports.map(_.reader(jsValue)).reduce(_ orElse _) orElse {
      case clazzName: String =>
        throw new NotSerializableException(s"No serialization supported for class $clazzName")
    }
  }
  override val writer: PartialFunction[Jsonable, JsValue] = serializationSupports.map(_.writer).reduce(_ orElse _) orElse {
    case jsonable: Jsonable =>
      throw new NotSerializableException(s"No serialization supported for $jsonable")
  }

  override def identifier: Int = 900188

  override def includeManifest: Boolean = false

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = manifest match {
    case None =>
      Json.parse(new String(bytes, "UTF-8")) match {
        case obj: JsObject => obj.fields match {
          case Seq(
          ("manifest", JsString(ConfigChangedClass)),
          ("config", config: JsValue),
          ("cmd", configcmd: JsValue)) =>
            ConfigChanged(configFromJson(config), jsonableFromJson(configcmd))
          case _ => jsonableFromJson(obj)
        }
        case JsString("UseDefault") => UseDefault
        case other => jsonableFromJson(other)
      }
    case Some(_) => throw new NotSerializableException("JsonSerializer does not use extra manifest.")
  }

  private def configFromJson(config: JsValue): Config = config.as[Config]

  private def jsonableFromJson(evt: JsValue): Jsonable = evt match {
    case obj: JsObject => obj.fields match {
      case Seq(
      ("manifest", JsString(clazzname)),
      ("payload", payload: JsValue)) =>
        reader.apply(payload).apply(clazzname)
    }
    case _ => throw new NotSerializableException(evt.toString())
  }

  private def toJson(o: AnyRef): JsValue = o match {
    case ConfigChanged(config, cmd) =>
      JsObject(
        Seq(
          "manifest" -> JsString(ConfigChangedClass),
          "config" -> Json.toJson(config),
          "cmd" -> toJson(cmd)
        )
      )
    case jsonable: Jsonable =>
      val payload = writer.apply(jsonable)
      JsObject(Seq(
        "manifest" -> JsString(jsonable.getClass.getName),
        "payload" -> payload
      ))
    case UseDefault => JsString("UseDefault")
  }

  override def toBinary(o: AnyRef): Array[Byte] = toJson(o).toString().getBytes("UTF-8")
}
