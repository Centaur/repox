package com.gtan.repox

import akka.serialization.Serializer

/**
 * Created by xf on 14/12/18.
 */
class JsonSerializer extends Serializer{
  override def identifier: Int = 900188

  override def includeManifest: Boolean = false

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = ???

  override def toBinary(o: AnyRef): Array[Byte] = o match {

  }
}
