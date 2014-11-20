package com.gtan.repox

import java.util.concurrent.TimeUnit

import org.jboss.netty.handler.timeout
import org.jboss.netty.handler.timeout.TimeoutException
import org.jboss.netty.util.{Timeout, TimerTask, HashedWheelTimer}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration.Duration

/**
 * Created by xf on 14/11/20.
 */
object TimeoutableFuture {
  val timer = new HashedWheelTimer(200, TimeUnit.MILLISECONDS)

  private def scheduleTimeout(name: String, promise: Promise[_], after: Duration) = {
    timer.newTimeout(new TimerTask {
      def run(timeout: Timeout) {
        promise.failure(new TimeoutException(s"future $name timeout (${after.toMillis} millis)"))
      }
    }, after.toNanos, TimeUnit.NANOSECONDS)
  }

  def apply[T](originalPromise: Promise[T], after: Duration, name: String)(implicit ec: ExecutionContext) = {
    val fut = originalPromise.future
    val prom = Promise[T]()
    val timeout = scheduleTimeout(name, prom, after)
    val combinedFut = Future.firstCompletedOf(List(fut, prom.future))
    fut onComplete { case result =>
      if(!timeout.isExpired){
        println(s"future $name canceled because of peer future completion")
      }
      timeout.cancel()
    }
    prom.future.onFailure {
      case e =>
        println(e.getMessage)
        if(!fut.isCompleted)
          originalPromise.failure(e)
    }
    combinedFut
  }
}
