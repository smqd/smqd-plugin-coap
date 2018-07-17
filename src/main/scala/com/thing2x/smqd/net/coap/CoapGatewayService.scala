package com.thing2x.smqd.net.coap

import java.util.concurrent.atomic.AtomicInteger

import com.thing2x.smqd.{ClientId, FilterPath, QoS, SmqSuccess, Smqd, TopicPath}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import org.eclipse.californium.core.CoapResource
import org.eclipse.californium.core.coap.CoAP.ResponseCode
import org.eclipse.californium.core.coap.{MediaTypeRegistry, Response}
import org.eclipse.californium.core.server.resources.{CoapExchange, Resource}

import scala.concurrent.Future
import scala.util.Success
import scala.util.matching.Regex

// 2018. 7. 17. - Created by Kwon, Yeong Eon

/**
  *
  */
class CoapGatewayService(name: String, smqdInstance: Smqd, config: Config) extends CoapService(name, smqdInstance, config) {

  private val prefix = "mqtt"
  private val removablePrefix = "/"+prefix+"/"
  private val clientIdentifierFormat: Regex = smqdInstance.config.getString("smqd.registry.client.identifier.format").r

  override def start(): Unit = {
    super.start()

    coapServer.add(new MqttResource(prefix))
  }

  override def stop(): Unit = {
    super.stop()
  }

  class MqttResource(name: String, visiable: Boolean = true) extends CoapResource(name, visiable) with StrictLogging {

    override def handleGET(exchange: CoapExchange): Unit = {
      val clientIdStr = exchange.getQueryParameter("c") // clientId
      val username = exchange.getQueryParameter("u") // username
      val password = exchange.getQueryParameter("p") // password

      val clientId = ClientId(clientIdStr, None)
      import smqdInstance.Implicit._
      // check clientId, username and password
      authorized(clientId, Option(username), Option(if (password == null) null else password.getBytes)) onComplete {
        case Success(code) => code match {
          case ResponseCode.VALID =>
            pathAsFilter match {
              case Some(filter) =>
                val observe = Option(exchange.getRequestOptions.getObserve)
                observe match {
                  case Some(obs) if obs.intValue() == 0 => subscribe(filter, clientId, Option(username), exchange)
                  case Some(obs) if obs.intValue() == 1 => unsubscribe(filter, clientId, Option(username), exchange)
                  case _ => // one-time query
                }
              case _ => // wrong topic filter format
                exchange.respond(ResponseCode.NOT_ACCEPTABLE, s"Invalid topic filter: $getPath")
            }
          case ResponseCode.UNAUTHORIZED =>  // authentication failed
            exchange.respond(code, s"Bad username or password")
          case ResponseCode.FORBIDDEN =>     // invalid identifier
            exchange.respond(code, s"Not allowed client identifier: $clientId")
          case _ =>
            exchange.respond(ResponseCode.BAD_REQUEST, s"Invalid client identifier or username: $clientId")
        }
        case _ =>
          exchange.respond(ResponseCode.INTERNAL_SERVER_ERROR, s"Uknown server error for eclient: $clientId")
      }
    }

    override def handlePUT(exchange: CoapExchange): Unit = {
      val ba = exchange.getRequestPayload
      val clientIdStr = exchange.getQueryParameter("c") // clientId
      val username = exchange.getQueryParameter("u") // username
      val password = exchange.getQueryParameter("p") // password

      val clientId = ClientId(clientIdStr, None)

      import smqdInstance.Implicit._

      // check clientId, username and password
      authorized(clientId, Option(username), Option(if (password == null) null else password.getBytes)) onComplete {
        // authentication success
        case Success(code) => code match {
          case ResponseCode.VALID =>
            pathAsTopic match {
              case Some(topic) =>
                publish(topic, clientId, Option(username), exchange, ba)
              case _ => // wrong topic format
                exchange.respond(ResponseCode.NOT_ACCEPTABLE, s"Invalid topic path: $getPath")
            }
          case ResponseCode.UNAUTHORIZED =>  // authentication failed
            exchange.respond(code, s"Bad username or password")
          case ResponseCode.FORBIDDEN =>     // invalid identifier
            exchange.respond(code, s"Not allowed client identifier: $clientId")
          case _ =>
            exchange.respond(ResponseCode.BAD_REQUEST, s"Invalid client identifier or username: $clientId")
        }
        case _ =>
          exchange.respond(ResponseCode.INTERNAL_SERVER_ERROR, s"Uknown server error for eclient: $clientId")
      }
    }

    private val notificationSerial = new AtomicInteger(1)

    private def subscribe(filterPath: FilterPath, clientId: ClientId, username: Option[String], exchange: CoapExchange): Unit = {
      import smqdInstance.Implicit._

      // check if the topic is allowed for subscribing
      smqdInstance.allowSubscribe(filterPath, QoS.AtLeastOnce, clientId, username) onComplete {
        case Success(qos) if qos != QoS.Failure =>
          if (exchange.getRequestOptions.getObserve != null) {
            exchange.accept()
            smqdInstance.subscribe(filterPath) {
              case (topic, msg) =>
                val response = new Response(ResponseCode.CONTENT)
                msg match {
                  case str: String =>
                    response.getOptions.setContentFormat(MediaTypeRegistry.TEXT_PLAIN)
                    response.setPayload(str)
                  case ba: Array[Byte] =>
                    response.setPayload(ba)
                  case obj: Any =>
                    response.getOptions.setContentFormat(MediaTypeRegistry.TEXT_PLAIN)
                    response.setPayload(obj.toString)
                }
                response.getOptions.setObserve(notificationSerial.getAndIncrement() % 0x00FFFFFF)
                exchange.respond(response)
            }
          }
        case _ =>
          exchange.respond(ResponseCode.UNAUTHORIZED, s"Not allowed topic: ${filterPath.toString}")
      }

    }

    private def unsubscribe(filterPath: FilterPath, clientId: ClientId, username: Option[String], exchange: CoapExchange): Unit = {
      exchange.accept()
    }

    private def publish(topicPath: TopicPath, clientId: ClientId, username: Option[String], exchange: CoapExchange, msg: Array[Byte]): Unit = {
      import smqdInstance.Implicit._

      // check if the topic is allowed for publishing
      smqdInstance.allowPublish(topicPath, clientId, username) onComplete {
        case Success(canPublish) if canPublish =>
          smqdInstance.publish(topicPath, msg)
          exchange.respond(ResponseCode.CREATED)
        case _ =>
          exchange.respond(ResponseCode.UNAUTHORIZED, s"Not allowed topic: ${topicPath.toString}")
      }
    }

    private def pathAsFilter: Option[FilterPath] = {
      val path = getPath
      if (path.startsWith(CoapGatewayService.this.removablePrefix)) {
        val filterPath = path.substring(CoapGatewayService.this.removablePrefix.length) + name
        FilterPath.parse(filterPath)
      }
      else {
        None
      }
    }

    private def pathAsTopic: Option[TopicPath] = {
      val path = getPath
      if (path.startsWith(CoapGatewayService.this.removablePrefix)) {
        val topicPath = path.substring(CoapGatewayService.this.removablePrefix.length) + name
        TopicPath.parse(topicPath)
      }
      else {
        None
      }
    }

    private def authorized(clientId: ClientId, username: Option[String], password: Option[Array[Byte]]): Future[ResponseCode] = {
      import smqdInstance.Implicit._

      // check clientId format
      clientId.id match {
        case clientIdentifierFormat(_*) => // valid clientId
          // check username and password
          smqdInstance.authenticate(clientId, username, password) flatMap {
            case SmqSuccess => Future(ResponseCode.VALID) // authentication success
            case _ => Future(ResponseCode.UNAUTHORIZED) // authentication failed
          }
        case _ => Future(ResponseCode.FORBIDDEN) // invalid clientId
      }
    }

    override def getChild(childName: String): Resource = {
      var child = super.getChild(childName)
      if (child == null) {
        child = new MqttResource(childName)
        add(child)
      }
      child
    }
  }
}

