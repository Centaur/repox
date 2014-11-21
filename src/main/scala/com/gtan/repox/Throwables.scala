package com.gtan.repox

import com.ning.http.client.HttpResponseStatus

/**
 * Created by xf on 14/11/21.
 */
case class UnsuccessResponseStatus(status: HttpResponseStatus) extends RuntimeException

case class PeerChosen(upstream: String) extends RuntimeException