/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.kuujo.vertigo.hooks;

import net.kuujo.vertigo.context.InstanceContext;
import net.kuujo.vertigo.context.MalformedContextException;
import net.kuujo.vertigo.message.DefaultMessageId;
import net.kuujo.vertigo.message.MessageId;

import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;

/**
 * An event bus hook listener.
 *
 * This class assists in listening for messages arriving on the event bus
 * via the {@link EventBusHook}.
 *
 * @author Jordan Halterman
 */
public class EventBusHookListener {
  private final String componentAddress;
  private final EventBus eventBus;
  private Handler<InstanceContext> startHandler;
  private Handler<MessageId> receiveHandler;
  private Handler<MessageId> ackHandler;
  private Handler<MessageId> failHandler;
  private Handler<MessageId> emitHandler;
  private Handler<MessageId> ackedHandler;
  private Handler<MessageId> failedHandler;
  private Handler<MessageId> timeoutHandler;
  private Handler<InstanceContext> stopHandler;

  public EventBusHookListener(String componentAddress, EventBus eventBus) {
    this.componentAddress = componentAddress;
    this.eventBus = eventBus;
    registerHandler();
  }

  /**
   * Registers the event bus handler.
   */
  private void registerHandler() {
    eventBus.registerHandler(String.format("vertigo.hooks.%s", componentAddress), new Handler<Message<JsonObject>>() {
      @Override
      public void handle(Message<JsonObject> message) {
        JsonObject body = message.body();
        if (body != null) {
          String event = body.getString("event");
          switch (event) {
            case "start":
              if (startHandler != null) {
                try {
                  startHandler.handle(InstanceContext.fromJson(body.getObject("context")));
                }
                catch (MalformedContextException e) {
                  // Do nothing.
                }
              }
              break;
            case "receive":
              if (receiveHandler != null) {
                receiveHandler.handle(DefaultMessageId.fromJson(body.getObject("id")));
              }
              break;
            case "ack":
              if (ackHandler != null) {
                ackHandler.handle(DefaultMessageId.fromJson(body.getObject("id")));
              }
              break;
            case "fail":
              if (failHandler != null) {
                failHandler.handle(DefaultMessageId.fromJson(body.getObject("id")));
              }
              break;
            case "emit":
              if (emitHandler != null) {
                emitHandler.handle(DefaultMessageId.fromJson(body.getObject("id")));
              }
              break;
            case "acked":
              if (ackedHandler != null) {
                ackedHandler.handle(DefaultMessageId.fromJson(body.getObject("id")));
              }
              break;
            case "failed":
              if (failedHandler != null) {
                failedHandler.handle(DefaultMessageId.fromJson(body.getObject("id")));
              }
              break;
            case "timeout":
              if (timeoutHandler != null) {
                timeoutHandler.handle(DefaultMessageId.fromJson(body.getObject("id")));
              }
              break;
            case "stop":
              if (stopHandler != null) {
                try {
                  stopHandler.handle(InstanceContext.fromJson(body.getObject("context")));
                }
                catch (MalformedContextException e) {
                  // Do nothing.
                }
              }
              break;
          }
        }
      }
    });
  }

  /**
   * Sets a start handler.
   *
   * @param startHandler
   *   A start handler.
   * @return
   *   The called listener instance.
   */
  public EventBusHookListener startHandler(Handler<InstanceContext> startHandler) {
    this.startHandler = startHandler;
    return this;
  }

  /**
   * Sets a receive handler.
   *
   * @param receiveHandler
   *   A receive handler.
   * @return
   *   The called listener instance.
   */
  public EventBusHookListener receiveHandler(Handler<MessageId> receiveHandler) {
    this.receiveHandler = receiveHandler;
    return this;
  }

  /**
   * Sets a ack handler.
   *
   * @param ackHandler
   *   A ack handler.
   * @return
   *   The called listener instance.
   */
  public EventBusHookListener ackHandler(Handler<MessageId> ackHandler) {
    this.ackHandler = ackHandler;
    return this;
  }

  /**
   * Sets a fail handler.
   *
   * @param failHandler
   *   A fail handler.
   * @return
   *   The called listener instance.
   */
  public EventBusHookListener failHandler(Handler<MessageId> failHandler) {
    this.failHandler = failHandler;
    return this;
  }

  /**
   * Sets an emit handler.
   *
   * @param emitHandler
   *   An emit handler.
   * @return
   *   The called listener instance.
   */
  public EventBusHookListener emitHandler(Handler<MessageId> emitHandler) {
    this.emitHandler = emitHandler;
    return this;
  }

  /**
   * Sets an acked handler.
   *
   * @param ackedHandler
   *   An acked handler.
   * @return
   *   The called listener instance.
   */
  public EventBusHookListener ackedHandler(Handler<MessageId> ackedHandler) {
    this.ackedHandler = ackedHandler;
    return this;
  }

  /**
   * Sets a failed handler.
   *
   * @param failedHandler
   *   A failed handler.
   * @return
   *   The called listener instance.
   */
  public EventBusHookListener failedHandler(Handler<MessageId> failedHandler) {
    this.failedHandler = failedHandler;
    return this;
  }

  /**
   * Sets a timeout handler.
   *
   * @param timeoutHandler
   *   A timeout handler.
   * @return
   *   The called listener instance.
   */
  public EventBusHookListener timeoutHandler(Handler<MessageId> timeoutHandler) {
    this.timeoutHandler = timeoutHandler;
    return this;
  }

  /**
   * Sets a stop handler.
   *
   * @param stopHandler
   *   A stop handler.
   * @return
   *   The called listener instance.
   */
  public EventBusHookListener stopHandler(Handler<InstanceContext> stopHandler) {
    this.stopHandler = stopHandler;
    return this;
  }

}
