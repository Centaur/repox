package com.gtan.repox

import java.io.NotSerializableException

import akka.actor.ExtendedActorSystem
import akka.serialization.Serializer
import com.gtan.repox.config._
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json.{JsValue, JsString, JsObject, Json}
import ConfigPersister._

trait SerializationSupport {
  val reader: JsValue => PartialFunction[String, Cmd]
  val writer: PartialFunction[Cmd, JsValue]
}

class JsonSerializer extends Serializer with LazyLogging with SerializationSupport {
  val ConfigChangedClass = classOf[ConfigChanged].getName

  val serializationSupports: Seq[_ <: SerializationSupport] = Seq(RepoPersister, ProxyPersister, ParameterPersister, Immediate404RulePersister, ExpireRulePersister, ConnectorPersister)

  override val reader = { jsValue: JsValue =>
    serializationSupports.map(_.reader(jsValue)).reduce(_ orElse _) orElse {
      case clazzName: String =>
        (throw new NotSerializableException(s"No serialization supported for class $clazzName")): Cmd
    }: PartialFunction[String, Cmd]
  }
  override val writer = serializationSupports.map(_.writer).reduce(_ orElse _) orElse {
    case cmd: Cmd => throw new NotSerializableException(s"No serialization supported for $cmd")
  }: PartialFunction[Cmd, JsValue]

  override def identifier: Int = 900188

  override def includeManifest: Boolean = false

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = manifest match {
    case None =>
      Json.parse(new String(bytes, "UTF-8")) match {
        case JsObject(Seq(("manifest", JsString(ConfigChangedClass)), ("config", config: JsValue), ("cmd", cmd: JsValue))) =>
          ConfigChanged(configFromJson(config), cmdFromJson(cmd))
        case JsString("UseDefault") => UseDefault
      }
    case Some(_) => throw new NotSerializableException("JsonSerializer does not use extra manifest.")
  }

  private def configFromJson(config: JsValue): Config = config.as[Config]

  private def cmdFromJson(cmd: JsValue): Cmd = cmd match {
    case JsObject(Seq(
    ("manifest", JsString(clazzname)),
    ("payload", payload: JsValue)
    )) =>
      reader.apply(payload).apply(clazzname)
    case _ => throw new NotSerializableException(cmd.toString())
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
    case cmd: Cmd =>
      val payload = writer.apply(cmd)
      JsObject(Seq(
        "manifest" -> JsString(cmd.getClass.getName),
        "payload" -> payload
      ))
    case UseDefault => JsString("UseDefault")
  }

  override def toBinary(o: AnyRef): Array[Byte] = toJson(o).toString().getBytes("UTF-8")
}
