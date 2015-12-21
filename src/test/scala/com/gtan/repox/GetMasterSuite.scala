package com.gtan.repox

import java.net.URLEncoder

import akka.actor.ActorPath
import org.scalatest._

class GetMasterSuite extends FunSuite{
  val src = Seq("ascii", "中文Path", "带 空 格", ".其$它*特@殊!字#符~:+")
  /**
    * URLEncoder is used to generate part of GetWorker/HeadWorker names
    */
  test("normalized actor paths are valid") {
    assert(src.forall{ path =>
      ActorPath.isValidPathElement(URLEncoder.encode(path, "UTF-8"))
    })
  }

}
