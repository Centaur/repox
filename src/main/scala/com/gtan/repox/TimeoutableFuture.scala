package com.gtan.repox

import java.util.concurrent.TimeUnit

import com.typesafe.scalalogging.LazyLogging
import org.jboss.netty.handler.timeout
import org.jboss.netty.handler.timeout.TimeoutException
import org.jboss.netty.util.{Timeout, TimerTask, HashedWheelTimer}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration.Duration

/**
 * Created by xf on 14/11/20.
 */
object TimeoutableFuture extends LazyLogging{
  val timer = new HashedWheelTimer(200, TimeUnit.MILLISECONDS)

  private def scheduleTimeout(name: String, promise: Promise[_], after: Duration) = {
    timer.newTimeout(new TimerTask {
      def run(timeout: Timeout) {
        promise.failure(new TimeoutException(s"future $name timeout (${after.toMillis} millis)"))
      }
    }, after.toNanos, TimeUnit.NANOSECONDS)
  }

  def apply[T](fut: Future[T], after: Duration, name: String)(implicit ec: ExecutionContext) = {
    val prom = Promise[T]()
    val timeout = scheduleTimeout(name, prom, after)
    val combinedFut = Future.firstCompletedOf(List(fut, prom.future))
    fut onComplete { case result =>
//      if(!timeout.isExpired){
//        logger.debug(s"timeout for $name canceled because of peer future completion")
//      }
      timeout.cancel()
    }
    combinedFut
  }
}
