package org.eclipse.californium.examples;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapHandler;
import org.eclipse.californium.core.CoapObserveRelation;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

// 2018. 7. 16. - Created by Kwon, Yeong Eon

/**
 *
 */
public class CoAPClientExample {
  private static Logger logger = LoggerFactory.getLogger("CoAPClientExample");

  public static void main(String[] args) {

    CoapClient client = new CoapClient("coap://127.0.0.1:5683/hello");

    logger.info("SYNCHRONOUS");

    // synchronous
    String content1 = client.get().getResponseText();
    logger.info("RESPONSE 1: " + content1);

    CoapResponse resp2 = client.post("payload", MediaTypeRegistry.TEXT_PLAIN);
    logger.info("RESPONSE 2 CODE: " + resp2.getCode());

    // asynchronous

    logger.info("ASYNCHRONOUS (press enter to continue)");

    client.get(new CoapHandler() {
      @Override public void onLoad(CoapResponse response) {
        String content = response.getResponseText();
        logger.info("RESPONSE 3: " + content);
      }

      @Override public void onError() {
        System.err.println("FAILED");
      }
    });

    // wait for user
    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    try { br.readLine(); } catch (IOException e) { }

    // observe

    logger.info("OBSERVE (press enter to exit)");

    CoapObserveRelation relation = client.observe(
            new CoapHandler() {
              @Override public void onLoad(CoapResponse response) {
                String content = response.getResponseText();
                logger.info("NOTIFICATION: " + content);
              }

              @Override public void onError() {
                logger.info("OBSERVING FAILED (press enter to exit)");
              }
            });

    // wait for user
    try { br.readLine(); } catch (IOException e) { }

    logger.info("CANCELLATION");

    relation.proactiveCancel();
  }
}
