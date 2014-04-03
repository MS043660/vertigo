/*
 * Copyright 2014 the original author or authors.
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
package net.kuujo.vertigo.output.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.kuujo.vertigo.cluster.VertigoCluster;
import net.kuujo.vertigo.cluster.data.AsyncMap;
import net.kuujo.vertigo.context.OutputConnectionContext;
import net.kuujo.vertigo.message.JsonMessage;
import net.kuujo.vertigo.message.MessageAcker;
import net.kuujo.vertigo.util.CountingCompletionHandler;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.impl.DefaultFutureResult;

/**
 * Output connection for exactly-once processing.
 *
 * @author Jordan Halterman
 */
public class ExactlyOnceOutputConnection extends BaseOutputConnection {
  private final Map<String, AsyncMap<String, String>> messages = new HashMap<>();
  private final Map<String, Integer> queueSizes = new HashMap<>();
  private boolean queueFull;

  public ExactlyOnceOutputConnection(Vertx vertx, OutputConnectionContext context, VertigoCluster cluster, MessageAcker acker) {
    super(vertx, context, cluster, acker);
    for (String address : targets) {
      messages.put(address, cluster.<String, String>getMap(String.format("%s.%s", context.address(), address)));
      queueSizes.put(address, 0);
    }
  }

  @Override
  public boolean sendQueueFull() {
    return queueFull;
  }

  @Override
  public void update(OutputConnectionContext update) {
    for (String address : update.targets()) {
      if (!targets.contains(address)) {
        targets.add(address);
        messages.put(address, cluster.<String, String>getMap(String.format("%s.%s", context.address(), address)));
        queueSizes.put(address, 0);
      }
    }
    for (String address : targets) {
      if (!update.targets().contains(address)) {
        targets.remove(address);
        messages.remove(address).clear();
        queueSizes.remove(address);
      }
    }
  }

  @Override
  public String send(JsonMessage message) {
    return send(message, (Handler<AsyncResult<Void>>) null);
  }

  @Override
  public String send(final JsonMessage message, final Handler<AsyncResult<Void>> doneHandler) {
    checkFull();
    final List<String> addresses = selector.select(message, targets);
    final Map<String, JsonMessage> children = new HashMap<>();
    for (String address : addresses) {
      children.put(address, createCopy(message));
    }

    final CountingCompletionHandler<Void> counter = new CountingCompletionHandler<Void>(children.size());
    counter.setHandler(new Handler<AsyncResult<Void>>() {
      @Override
      public void handle(AsyncResult<Void> result) {
        if (result.failed()) {
          new DefaultFutureResult<Void>(result.cause()).setHandler(doneHandler);
        } else {
          new DefaultFutureResult<Void>((Void) null).setHandler(doneHandler);
          doSend(children);
        }
      }
    });

    for (Map.Entry<String, JsonMessage> entry : children.entrySet()) {
      final String address = entry.getKey();
      messages.get(address).put(entry.getValue().id(), serializer.serializeToString(entry.getValue()), new Handler<AsyncResult<String>>() {
        @Override
        public void handle(AsyncResult<String> result) {
          if (result.failed()) {
            counter.fail(result.cause());
          } else {
            queueSizes.put(address, queueSizes.get(address)+1);
            if (queueSizes.get(address) >= maxQueueSize) queueFull = true;
            checkPause();
            counter.succeed();
          }
        }
      });
    }
    return message.id();
  }

  @Override
  public String send(JsonMessage message, JsonMessage parent) {
    return send(message, parent, null);
  }

  @Override
  public String send(final JsonMessage message, final JsonMessage parent, final Handler<AsyncResult<Void>> doneHandler) {
    checkFull();
    final List<String> addresses = selector.select(message, targets);
    final Map<String, JsonMessage> children = new HashMap<>();
    for (String address : addresses) {
      JsonMessage child = createCopy(message);
      acker.anchor(parent, child); // Anchor the child messages to the parent.
      children.put(address, child);
    }

    final CountingCompletionHandler<Void> counter = new CountingCompletionHandler<Void>(children.size());
    counter.setHandler(new Handler<AsyncResult<Void>>() {
      @Override
      public void handle(AsyncResult<Void> result) {
        if (result.failed()) {
          new DefaultFutureResult<Void>(result.cause()).setHandler(doneHandler);
        } else {
          new DefaultFutureResult<Void>((Void) null).setHandler(doneHandler);
          doSend(children);
        }
      }
    });

    for (Map.Entry<String, JsonMessage> entry : children.entrySet()) {
      final String address = entry.getKey();
      messages.get(address).put(entry.getValue().id(), serializer.serializeToString(entry.getValue()), new Handler<AsyncResult<String>>() {
        @Override
        public void handle(AsyncResult<String> result) {
          if (result.failed()) {
            counter.fail(result.cause());
          } else {
            acker.ack(parent); // Ack the child messages since they have been replicated.
            queueSizes.put(address, queueSizes.get(address)+1);
            if (queueSizes.get(address) >= maxQueueSize) queueFull = true;
            checkPause();
            counter.succeed();
          }
        }
      });
    }
    return message.id();
  }

  /**
   * Sends a message.
   */
  private void doSend(Map<String, JsonMessage> messages) {
    for (Map.Entry<String, JsonMessage> entry : messages.entrySet()) {
      doSend(entry.getKey(), entry.getValue());
    }
  }

  /**
   * Sends a message.
   */
  private void doSend(final String address, final JsonMessage message) {
    vertx.eventBus().sendWithTimeout(address, serializer.serializeToString(message), 30000, new Handler<AsyncResult<Message<Boolean>>>() {
      @Override
      public void handle(AsyncResult<Message<Boolean>> result) {
        if (result.failed() || !result.result().body()) {
          doSend(address, message);
        } else {
          messages.get(address).remove(message.id(), new Handler<AsyncResult<String>>() {
            @Override
            public void handle(AsyncResult<String> result) {
              if (result.succeeded()) {
                queueSizes.put(address, queueSizes.get(address)-1);
                if (queueFull && queueSizes.get(address) < maxQueueSize) {
                  boolean isFull = false;
                  for (int size : queueSizes.values()) {
                    if (size >= maxQueueSize) {
                      isFull = true;
                      break;
                    }
                  }
                  if (!isFull) {
                    queueFull = false;
                  }
                }
                checkPause();
              }
            }
          });
        }
      }
    });
  }

}
