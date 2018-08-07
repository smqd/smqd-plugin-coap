//
// Copyright (c) 2018 UANGEL
//
// All rights reserved. This program and the accompanying materials
// are made available under the terms of the Eclipse Public License v2.0
// and Eclipse Distribution License v1.0 which accompany this distribution.
//
// The Eclipse Public License is available at
//    http://www.eclipse.org/legal/epl-v20.html
// and the Eclipse Distribution License is available at
//    http://www.eclipse.org/org/documents/edl-v10.html.
//     

package com.thing2x.smqd.net.coap

import com.thing2x.smqd.Smqd
import com.thing2x.smqd.protocol.{ProtocolDirection, ProtocolNotification, Recv, Send}
import com.typesafe.scalalogging.StrictLogging
import org.eclipse.californium.core.coap.{EmptyMessage, Message, Request, Response}
import org.eclipse.californium.core.network.interceptors.MessageInterceptor
import org.eclipse.californium.elements._

// 2018. 7. 16. - Created by Kwon, Yeong Eon

/**
  *
  */
class CoapTracer(smqdInstance: Smqd) extends MessageInterceptor with StrictLogging {

  private def noti(dir: ProtocolDirection, msg: Message): CoapProtocolNotification = {
    val epc = dir match {
      case Send => msg.getDestinationContext
      case Recv => msg.getSourceContext
      case _ => msg.getDestinationContext
    }

    val addr = epc.getPeerAddress
    val channel = epc match {
      case _: DtlsEndpointContext => "coap+dtls://"+addr.getHostString+":"+addr.getPort
      case _: UdpEndpointContext => "coap+udp://"+addr.getHostString+":"+addr.getPort
      case _: TlsEndpointContext => "coap+tls://"+addr.getHostString+":"+addr.getPort
      case _: TcpEndpointContext => "coap+tcp://"+addr.getHostString+":"+addr.getPort
      case _ => addr.getHostString+":"+addr.getPort
    }

    val message = msg match {
      case req: Request => (s"Req ${req.getType}-${req.getCode}", s"MID=${req.getMID}, Token=${req.getTokenString}, OptionSet=${req.getOptions}, ${req.getPayloadString}")
      case rsp: Response => (s"Rsp ${rsp.getType}-${rsp.getCode}", s"MID=${rsp.getMID}, Token=${rsp.getTokenString}, OptionSet=${rsp.getOptions}, ${rsp.getPayloadString}")
      case emp: EmptyMessage => (s"${emp.getType} ${emp.getMID}", emp.toString)
      case _ => (s"Msg ${msg.getType} ${msg.getRawCode}", msg.toString)
    }

    CoapProtocolNotification(channel, "-", message._1, message._2, dir)
  }

  override def sendRequest(request: Request): Unit =
    smqdInstance.notifyProtocol(noti(Send, request))

  override def sendResponse(response: Response): Unit =
    smqdInstance.notifyProtocol(noti(Send, response))

  override def sendEmptyMessage(message: EmptyMessage): Unit =
    smqdInstance.notifyProtocol(noti(Send, message))

  override def receiveRequest(request: Request): Unit =
    smqdInstance.notifyProtocol(noti(Recv, request))

  override def receiveResponse(response: Response): Unit =
    smqdInstance.notifyProtocol(noti(Recv, response))

  override def receiveEmptyMessage(message: EmptyMessage): Unit =
    smqdInstance.notifyProtocol(noti(Recv, message))
}

case class CoapProtocolNotification(channelId: String, clientId: String, messageType: String, message: String, direction: ProtocolDirection ) extends ProtocolNotification