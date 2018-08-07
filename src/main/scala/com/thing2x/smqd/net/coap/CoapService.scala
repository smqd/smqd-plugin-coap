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

import java.util

import com.thing2x.smqd.Smqd
import com.thing2x.smqd.plugin.Service
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import org.eclipse.californium.core.CoapResource
import org.eclipse.californium.core.coap.CoAP.ResponseCode
import org.eclipse.californium.core.network.Endpoint
import org.eclipse.californium.core.network.config.NetworkConfig
import org.eclipse.californium.core.server.resources.{CoapExchange, Resource}

import scala.collection.JavaConverters._

// 2018. 7. 16. - Created by Kwon, Yeong Eon

/**
  * General CoAP service plugin
  */
class CoapService(name: String, smqdInstance: Smqd, config: Config) extends Service(name, smqdInstance, config) with StrictLogging {

  protected var coapServer: CoapServer = _

  override def start(): Unit = {

    val netConf = networkConfig(config)

    coapServer = new CoapServer(netConf)
    coapServer.start()

    val tracer = new CoapTracer(smqdInstance)

    coapServer.getEndpoints.asScala.foreach { ep =>
      logger.info(s"Add a tracer on ${ep.getUri.toString}")
      ep.addInterceptor(tracer)
    }
  }

  override def stop(): Unit = {
    if (coapServer != null) {
      coapServer.stop()
    }
  }

  class CoapServer(netCfg: NetworkConfig) extends org.eclipse.californium.core.CoapServer(netCfg) {
    override def createRoot(): Resource = new RootResource()
  }

  private class RootResource extends CoapResource("") {
    override def handleGET(exchange: CoapExchange): Unit = {
      exchange.respond(ResponseCode.CONTENT, s"UANGEL CoAP Server (RFC 7252) - ${smqdInstance.version}")
    }

    override def getEndpoints: util.List[Endpoint] = coapServer.getEndpoints
  }


  private class HelloWorld extends CoapResource("hello") {
    override def handleGET(exchange: CoapExchange): Unit = {
      exchange.respond(ResponseCode.CONTENT, "world")
    }
  }

  private def networkConfig(config: Config): NetworkConfig = {

    val netConf = NetworkConfig.createStandardWithoutFile()
    config.entrySet.asScala foreach { entry =>
      val k = entry.getKey.toUpperCase
      val v = entry.getValue.render
        .replaceAll("^[\"]", "")    // removing quote mark
        .replaceAll("[\"]$", "")

      netConf.setString(k, v)
    }
    netConf
  }

  //ACK_RANDOM_FACTOR=1.5
  //ACK_TIMEOUT_SCALE=2.0
  //ACK_TIMEOUT=2000
  //BLOCKWISE_STATUS_LIFETIME=300000
  //COAP_PORT=5683
  //COAP_SECURE_PORT=5684
  //CONGESTION_CONTROL_ALGORITHM=Cocoa
  //CROP_ROTATION_PERIOD=2000
  //DEDUPLICATOR=DEDUPLICATOR_MARK_AND_SWEEP
  //DTLS_AUTO_RESUME_TIMEOUT=30000
  //EXCHANGE_LIFETIME=247000
  //HEALTH_STATUS_INTERVAL=0
  //HTTP_CACHE_RESPONSE_MAX_AGE=86400
  //HTTP_CACHE_SIZE=32
  //HTTP_PORT=8080
  //HTTP_SERVER_SOCKET_BUFFER_SIZE=8192
  //HTTP_SERVER_SOCKET_TIMEOUT=100000
  //LEISURE=5000
  //MARK_AND_SWEEP_INTERVAL=10000
  //MAX_ACTIVE_PEERS=150000
  //MAX_MESSAGE_SIZE=1024
  //MAX_PEER_INACTIVITY_PERIOD=600
  //MAX_RESOURCE_BODY_SIZE=8192
  //MAX_RETRANSMIT=4
  //MAX_TRANSMIT_WAIT=93000
  //MID_TACKER=GROUPED
  //MID_TRACKER_GROUPS=16
  //NETWORK_STAGE_RECEIVER_THREAD_COUNT=1
  //NETWORK_STAGE_SENDER_THREAD_COUNT=1
  //NON_LIFETIME=145000
  //NOTIFICATION_CHECK_INTERVAL_COUNT=100
  //NOTIFICATION_CHECK_INTERVAL=86400000
  //NOTIFICATION_REREGISTRATION_BACKOFF=2000
  //NSTART=1
  //PREFERRED_BLOCK_SIZE=512
  //PROBING_RATE=1.0
  //PROTOCOL_STAGE_THREAD_COUNT=8
  //RESPONSE_MATCHING=STRICT
  //SECURE_SESSION_TIMEOUT=86400
  //TCP_CONNECT_TIMEOUT=10000
  //TCP_CONNECTION_IDLE_TIMEOUT=10
  //TCP_WORKER_THREADS=1
  //TOKEN_SIZE_LIMIT=8
  //UDP_CONNECTOR_DATAGRAM_SIZE=2048
  //UDP_CONNECTOR_OUT_CAPACITY=2147483647
  //UDP_CONNECTOR_RECEIVE_BUFFER=0
  //UDP_CONNECTOR_SEND_BUFFER=0
  //USE_CONGESTION_CONTROL=false
  //USE_RANDOM_MID_START=true
}
