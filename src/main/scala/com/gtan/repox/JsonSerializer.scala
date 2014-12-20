package com.gtan.repox

import java.io.NotSerializableException

import akka.actor.ExtendedActorSystem
import akka.serialization.Serializer
import com.gtan.repox.config._
import play.api.libs.json.{JsValue, JsString, JsObject, Json}
import ConfigPersister._

/**
 * Created by xf on 14/12/18.
 */
class JsonSerializer(val system: ExtendedActorSystem) extends Serializer(system) {
  val ConfigChangedClass = classOf[ConfigChanged].getName
  val NewRepoClass = classOf[NewRepo].getName
  val DisableRepoClass = classOf[DisableRepo].getName
  val EnableRepoClass = classOf[EnableRepo].getName
  val DeleteRepoClass = classOf[DeleteRepo].getName

  override def identifier: Int = 900188

  override def includeManifest: Boolean = false

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = manifest match {
    case None =>
      Json.parse(new String(bytes, "UTF-8")) match {
        case JsObject(Seq(
        ("manifest", JsString(ConfigChangedClass)),
        ("config", config),
        ("cmd", cmd))) => ConfigChanged(configFromJson(config), cmdFromJson(cmd))
        case JsString("UseDefault") => UseDefault
      }
    case Some(_) => throw new NotSerializableException("JsonSerializer does not use extra manifest.")
  }


  private def configFromJson(config: JsValue): Config = config.as[Config]

  private def cmdFromJson(cmd: JsValue): Cmd = cmd match {
    case JsObject(Seq(
    ("manifest", JsString(clazzname)),
    ("payload", payload)
    )) => clazzname match {
      case NewRepoClass => payload.as[NewRepo]
      case DisableRepoClass => payload.as[DisableRepo]
      case EnableRepoClass => payload.as[EnableRepo]
      case DeleteRepoClass => payload.as[DeleteRepo]
    }
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
    case cmd: Cmd => cmd.serializeToJson
    case UseDefault => JsString("UseDefault")
  }

  override def toBinary(o: AnyRef): Array[Byte] = toJson(o).toString().getBytes("UTF-8")
}
