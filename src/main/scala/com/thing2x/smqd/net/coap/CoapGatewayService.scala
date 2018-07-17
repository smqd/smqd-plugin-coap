package com.thing2x.smqd.net.coap

import com.thing2x.smqd.{ClientId, SmqSuccess, Smqd, TopicPath}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import org.eclipse.californium.core.CoapResource
import org.eclipse.californium.core.coap.CoAP.{CodeClass, ResponseCode}
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
    }

    override def handlePUT(exchange: CoapExchange): Unit = {
      val ba = exchange.getRequestPayload
      val clientIdStr = exchange.getQueryParameter("c") // clientId
      val username = exchange.getQueryParameter("u") // username
      val password = exchange.getQueryParameter("p") // password

      val clientId = ClientId(clientIdStr, None)
      // check clientId format
      clientId.id match {
        case clientIdentifierFormat(_*) =>
          import smqdInstance.Implicit._

          // check username and password
          authorized(clientId, Option(username), Option(if (password == null) null else password.getBytes)) onComplete {

            // authentication success
            case Success(code) =>
              code match {
                case ResponseCode.VALID =>
                  pathAsTopic match {
                    case Some(topic) =>
                      // check if the topic is allowed for publishing
                      smqdInstance.allowPublish(topic, clientId, Option(username)) onComplete {
                        case Success(canPublish) if canPublish =>
                          smqdInstance.publish(topic, ba)
                          exchange.respond(ResponseCode.CREATED)
                        case _ =>
                          exchange.respond(ResponseCode.UNAUTHORIZED, s"Not allowed topic: ${topic.toString}")
                      }
                    case _ => // wrong topic format
                      exchange.respond(ResponseCode.NOT_ACCEPTABLE, s"Invalid topic path: ${getPath}")
                  }
                case ResponseCode.UNAUTHORIZED =>  // authentication failed
                  exchange.respond(code, s"Bad username or password")
                case ResponseCode.FORBIDDEN =>     // invalid identifier
                  exchange.respond(code, s"Invalid client identifier: $clientId")
                case _ =>
                  exchange.respond(ResponseCode.BAD_REQUEST, s"Invalid client identifier or username: $clientId")
              }
            case _ =>
              exchange.respond(ResponseCode.INTERNAL_SERVER_ERROR, s"Uknown server error for eclient: $clientId")
          }
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
            case SmqSuccess =>
              Future(ResponseCode.VALID) // authentication success
            case _ =>
              Future(ResponseCode.UNAUTHORIZED) // authentication failed
          }
        case _ => // invalid clientId
          Future(ResponseCode.FORBIDDEN)
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

