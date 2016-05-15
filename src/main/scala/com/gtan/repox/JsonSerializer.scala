package com.gtan.repox

import java.io.NotSerializableException

import akka.serialization.Serializer
import com.gtan.repox.config._
import com.typesafe.scalalogging.LazyLogging
import io.circe.Decoder.Result
import io.circe.Json.JString
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._


trait SerializationSupport {
  val reader: Json => PartialFunction[String, Result[Jsonable]]
  val writer: PartialFunction[Jsonable, Json]
}

class JsonSerializer extends Serializer with LazyLogging with SerializationSupport {

  import CirceCodecs._

  val ConfigChangedClass = classOf[ConfigChanged].getName

  val serializationSupports: Seq[_ <: SerializationSupport] = Seq(RepoPersister, ProxyPersister, ParameterPersister, Immediate404RulePersister, ExpireRulePersister, ConnectorPersister, ExpirationManager, ConfigPersister)

  override val reader: Json => PartialFunction[String, Result[Jsonable]] = { json =>
    serializationSupports.map(_.reader(json)).reduce(_ orElse _) orElse {
      case clazzName: String =>
        throw new NotSerializableException(s"No serialization supported for class $clazzName")
    }
  }
  override val writer: PartialFunction[Jsonable, Json] = serializationSupports.map(_.writer).reduce(_ orElse _) orElse {
    case jsonable: Jsonable =>
      throw new NotSerializableException(s"No serialization supported for $jsonable")
  }

  override def identifier: Int = 900188

  override def includeManifest: Boolean = false

  case class ConfigChangedPayload(manifest: String, config: Json, cmd: Json)

  case class JsonablePayload(manifest: String, payload: Json)

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = manifest match {
    case None =>
      import cats.data.Xor._
      val str = new String(bytes, "UTF-8")
      parse(str) match {
        case Right(json) =>
          val maybeObj = json.asObject
          val result = for {
            obj <- maybeObj
            mani <- obj("manifest") if mani.asString.contains(ConfigChangedClass)
            config <- obj("config")
            cmd <- obj("cmd")
          } yield ConfigChanged(configFromJson(config), jsonableFromJson(cmd))
          val otherPossibility = result
            .orElse(Some(jsonableFromJson(json)))
            .orElse(json.asString filter (_ == "UseDefault") map (_ => UseDefault))
            .getOrElse(throw new NotSerializableException())
          otherPossibility
        case Left(error) =>
          throw new NotSerializableException(error.getMessage)
      }
    case Some(_) => throw new NotSerializableException("JsonSerializer does not use extra manifest.")
  }

  private def configFromJson(config: Json): Config = config.as[Config].getOrElse(throw new NotSerializableException(config.toString))

  private def jsonableFromJson(evt: Json): Jsonable = {
    val opt = for {
      obj <- evt.asObject
      mani <- obj("manifest") if mani.isString
      manifest <- mani.asString
      payload <- obj("payload")
    } yield {
      reader.apply(payload).apply(manifest).fold(
        throw _, identity
      )
    }
    opt.get
  }


  private def toJson(o: AnyRef): Json = o match {
    case ConfigChanged(config, jsonable) =>
      Json.obj(
        "manifest" -> classOf[ConfigChanged].getName.asJson,
        "config" -> config.asJson,
        "cmd" -> toJson(jsonable)
      )
    case jsonable: Jsonable =>
      Json.obj(
        "manifest" -> jsonable.getClass.getName.asJson,
        "payload" -> writer.apply(jsonable)
      )
    case UseDefault => "UseDefault".asJson
  }

  override def toBinary(o: AnyRef): Array[Byte] = toJson(o).toString().getBytes("UTF-8")
}
