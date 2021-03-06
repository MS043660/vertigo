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
package net.kuujo.vertigo.io.connection.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import net.kuujo.vertigo.hook.OutputHook;
import net.kuujo.vertigo.io.connection.OutputConnection;
import net.kuujo.vertigo.io.connection.OutputConnectionContext;
import net.kuujo.vertigo.io.group.OutputGroup;
import net.kuujo.vertigo.io.impl.OutputSerializer;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.eventbus.ReplyException;
import org.vertx.java.core.eventbus.ReplyFailure;
import org.vertx.java.core.impl.DefaultFutureResult;
import org.vertx.java.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

/**
 * Default output connection implementation.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class DefaultOutputConnection implements OutputConnection {
  private static final int DEFAULT_MAX_QUEUE_SIZE = 1000;
  private final Logger log;
  private final Vertx vertx;
  private final EventBus eventBus;
  private final OutputConnectionContext context;
  private final String outAddress;
  private final String inAddress;
  private final OutputSerializer serializer = new OutputSerializer();
  private List<OutputHook> hooks = new ArrayList<>();
  private int maxQueueSize = DEFAULT_MAX_QUEUE_SIZE;
  private Handler<Void> drainHandler;
  private long currentMessage = 1;
  private final TreeMap<Long, JsonObject> messages = new TreeMap<>();
  private final Map<String, DefaultConnectionOutputGroup> groups = new HashMap<>();
  private DefaultConnectionOutputBatch currentBatch;
  private boolean open;
  private boolean full;
  private boolean paused;

  private final Handler<Message<JsonObject>> internalMessageHandler = new Handler<Message<JsonObject>>() {
    @Override
    public void handle(Message<JsonObject> message) {
      String action = message.body().getString("action");
      if (action != null) {
        switch (action) {
          case "group":
            doStartGroup(message.body().getString("group"));
            break;
          case "batch":
            doStartBatch(message.body().getString("batch"));
            break;
          case "ack":
            doAck(message.body().getLong("id"));
            break;
          case "fail":
            doFail(message.body().getLong("id"));
            break;
          case "pause":
            doPause(message.body().getLong("id"));
            break;
          case "resume":
            doResume(message.body().getLong("id"));
            break;
        }
      }
    }
  };

  public DefaultOutputConnection(Vertx vertx, String address) {
    this(vertx, DefaultOutputConnectionContext.Builder.newBuilder().setAddress(address).build());
  }

  public DefaultOutputConnection(Vertx vertx, OutputConnectionContext context) {
    this.vertx = vertx;
    this.eventBus = vertx.eventBus();
    this.context = context;
    this.hooks = context.hooks();
    this.outAddress = String.format("%s.out", context.address());
    this.inAddress = String.format("%s.in", context.address());
    this.log = LoggerFactory.getLogger(String.format("%s-%s", DefaultOutputConnection.class.getName(), context.target()));
  }

  @Override
  public String address() {
    return context.address();
  }

  @Override
  public OutputConnectionContext context() {
    return context;
  }

  @Override
  public Vertx vertx() {
    return vertx;
  }

  @Override
  public OutputConnection open() {
    return open(null);
  }

  @Override
  public OutputConnection open(final Handler<AsyncResult<Void>> doneHandler) {
    eventBus.registerHandler(outAddress, internalMessageHandler, new Handler<AsyncResult<Void>>() {
      @Override
      public void handle(AsyncResult<Void> result) {
        if (result.failed()) {
          new DefaultFutureResult<Void>(result.cause()).setHandler(doneHandler);
        } else {
          connect(doneHandler);
        }
      }
    });
    return this;
  }

  /**
   * Connects to the other side of the connection.
   */
  private void connect(final Handler<AsyncResult<Void>> doneHandler) {
    // Recursively send "connect" messages to the other side of the connection
    // until we get a response. This gives the other side of the connection time
    // to open and ensures that the connection doesn't claim it's open until
    // the other side has registered a handler and responded at least once.
    eventBus.sendWithTimeout(inAddress, new JsonObject().putString("action", "connect"), 1000, new Handler<AsyncResult<Message<Boolean>>>() {
      @Override
      public void handle(AsyncResult<Message<Boolean>> result) {
        if (result.failed()) {
          ReplyException failure = (ReplyException) result.cause();
          if (failure.failureType().equals(ReplyFailure.RECIPIENT_FAILURE)) {
            log.warn(String.format("%s - Failed to connect to %s", DefaultOutputConnection.this, context.target()), result.cause());
            new DefaultFutureResult<Void>(failure).setHandler(doneHandler);
          } else if (failure.failureType().equals(ReplyFailure.TIMEOUT)) {
            log.warn(String.format("%s - Connection to %s failed, retrying", DefaultOutputConnection.this, context.target()));
            connect(doneHandler);
          } else {
            log.debug(String.format("%s - Connection to %s failed, retrying", DefaultOutputConnection.this, context.target()));
            vertx.setTimer(500, new Handler<Long>() {
              @Override
              public void handle(Long timerID) {
                connect(doneHandler);
              }
            });
          }
        } else if (result.result().body()) {
          log.info(String.format("%s - Connected to %s", DefaultOutputConnection.this, context.target()));
          open = true;
          new DefaultFutureResult<Void>((Void) null).setHandler(doneHandler);
        } else {
          log.debug(String.format("%s - Connection to %s failed, retrying", DefaultOutputConnection.this, context.target()));
          vertx.setTimer(500, new Handler<Long>() {
            @Override
            public void handle(Long timerID) {
              connect(doneHandler);
            }
          });
        }
      }
    });
  }

  @Override
  public OutputConnection setSendQueueMaxSize(int maxSize) {
    this.maxQueueSize = maxSize;
    return this;
  }

  @Override
  public int getSendQueueMaxSize() {
    return maxQueueSize;
  }

  @Override
  public int size() {
    return messages.size();
  }

  @Override
  public boolean sendQueueFull() {
    return paused || messages.size() >= maxQueueSize;
  }

  @Override
  public OutputConnection drainHandler(Handler<Void> handler) {
    this.drainHandler = handler;
    return this;
  }

  @Override
  public OutputConnection batch(final String id, final Object args, final Handler<ConnectionOutputBatch> handler) {
    // If there's already a batch open then don't open the new batch until
    // the previous batch has been ended. This ensures that only one batch
    // can be open at any given time on the connection.
    if (currentBatch != null) {
      currentBatch.endHandler(new Handler<Void>() {
        @Override
        public void handle(Void _) {
          currentBatch = new DefaultConnectionOutputBatch(id, args, DefaultOutputConnection.this);
          currentBatch.start(handler);
        }
      });
    } else {
      currentBatch = new DefaultConnectionOutputBatch(id, args, this);
      currentBatch.start(handler);
    }
    return this;
  }

  @Override
  public OutputConnection group(Handler<OutputGroup> handler) {
    return group(UUID.randomUUID().toString(), null, handler);
  }

  @Override
  public OutputConnection group(String name, Handler<OutputGroup> handler) {
    return group(name, null, handler);
  }

  @Override
  public OutputConnection group(String name, Object args, Handler<OutputGroup> handler) {
    DefaultConnectionOutputGroup group = new DefaultConnectionOutputGroup(UUID.randomUUID().toString(), name, args, this);
    groups.put(group.id(), group);
    group.start(handler);
    return this;
  }

  DefaultConnectionOutputGroup group(String name, Object args, String parent, Handler<OutputGroup> handler) {
    DefaultConnectionOutputGroup group = new DefaultConnectionOutputGroup(UUID.randomUUID().toString(), name, args, parent, this);
    groups.put(group.id(), group);
    group.start(handler);
    return group;
  }

  @Override
  public void close() {
    close(null);
  }

  @Override
  public void close(final Handler<AsyncResult<Void>> doneHandler) {
    eventBus.unregisterHandler(outAddress, internalMessageHandler, new Handler<AsyncResult<Void>>() {
      @Override
      public void handle(AsyncResult<Void> result) {
        if (result.failed()) {
          new DefaultFutureResult<Void>(result.cause()).setHandler(doneHandler);
        } else {
          disconnect(doneHandler);
        }
      }
    });
  }

  /**
   * Disconnects from the other side of the connection.
   */
  private void disconnect(final Handler<AsyncResult<Void>> doneHandler) {
    eventBus.sendWithTimeout(inAddress, new JsonObject().putString("action", "disconnect"), 5000, new Handler<AsyncResult<Message<Boolean>>>() {
      @Override
      public void handle(AsyncResult<Message<Boolean>> result) {
        if (result.failed()) {
          ReplyException failure = (ReplyException) result.cause();
          if (failure.failureType().equals(ReplyFailure.RECIPIENT_FAILURE)) {
            log.warn(String.format("%s - Failed to disconnect from %s", DefaultOutputConnection.this, context.target()), result.cause());
            new DefaultFutureResult<Void>(failure).setHandler(doneHandler);
          } else if (failure.failureType().equals(ReplyFailure.NO_HANDLERS)) {
            log.info(String.format("%s - Disconnected from %s", DefaultOutputConnection.this, context.target()));
            new DefaultFutureResult<Void>((Void) null).setHandler(doneHandler);
          } else {
            log.debug(String.format("%s - Disconnection from %s failed, retrying", DefaultOutputConnection.this, context.target()));
            disconnect(doneHandler);
          }
        } else if (result.result().body()) {
          log.info(String.format("%s - Disconnected from %s", DefaultOutputConnection.this, context.target()));
          open = false;
          new DefaultFutureResult<Void>((Void) null).setHandler(doneHandler);
        } else {
          log.debug(String.format("%s - Disconnection from %s failed, retrying", DefaultOutputConnection.this, context.target()));
          vertx.setTimer(500, new Handler<Long>() {
            @Override
            public void handle(Long timerID) {
              disconnect(doneHandler);
            }
          });
        }
      }
    });
  }

  /**
   * Checks whether the connection is open.
   */
  private void checkOpen() {
    if (!open) throw new IllegalStateException(String.format("%s - Connection to %s not open.", this, context.target()));
  }

  /**
   * Checks whether the connection is full.
   */
  private void checkFull() {
    if (!full && messages.size() >= maxQueueSize) {
      full = true;
      log.debug(String.format("%s - Connection to %s is full", this, context.target()));
    }
  }

  /**
   * Checks whether the connection has been drained.
   */
  private void checkDrain() {
    if (full && !paused && messages.size() < maxQueueSize / 2) {
      full = false;
      log.debug(String.format("%s - Connection to %s is drained", this, context.target()));
      if (drainHandler != null) {
        drainHandler.handle((Void) null);
      }
    }
  }

  /**
   * Handles a group start.
   */
  private void doStartGroup(String groupID) {
    DefaultConnectionOutputGroup group = groups.get(groupID);
    if (group != null) {
      group.handleStart();
    }
  }

  /**
   * Handles a batch start.
   */
  private void doStartBatch(String batchID) {
    if (currentBatch != null && currentBatch.id().equals(batchID)) {
      currentBatch.handleStart();
    }
  }

  /**
   * Handles a batch ack.
   */
  private void doAck(long id) {
    // The other side of the connection has sent a message indicating which
    // messages it has seen. We can clear any messages before the indicated ID.
    if (log.isDebugEnabled()) {
      log.debug(String.format("%s - Received ack for messages up to %d, removing all previous messages from memory", this, id));
    }
    if (messages.containsKey(id+1)) {
      messages.tailMap(id+1);
    } else {
      messages.clear();
    }
    checkDrain();
  }

  /**
   * Handles a batch fail.
   */
  private void doFail(long id) {
    if (log.isDebugEnabled()) {
      log.debug(String.format("%s - Received resend request for messages starting at %d", this, id));
    }

    // Ack all the entries before the given ID.
    doAck(id);

    // Now that all the entries before the given ID have been removed,
    // just iterate over the messages map and resend all the messages.
    Iterator<Map.Entry<Long, JsonObject>> iter = messages.entrySet().iterator();
    while (iter.hasNext()) {
      eventBus.send(inAddress, iter.next().getValue());
    }
  }

  /**
   * Handles a connection pause.
   */
  private void doPause(long id) {
    log.debug(String.format("%s - Paused connection to %s", this, context.target()));
    paused = true;
  }

  /**
   * Handles a connection resume.
   */
  private void doResume(long id) {
    if (paused) {
      log.debug(String.format("%s - Resumed connection to %s", this, context.target()));
      paused = false;
      checkDrain();
    }
  }

  /**
   * Sends a message.
   */
  private OutputConnection doSend(final Object value) {
    checkOpen();
    JsonObject message = createMessage(value)
        .putString("action", "message");
    if (open && !paused) {
      if (log.isDebugEnabled()) {
        log.debug(String.format("%s - Send: Message[id=%d, message=%s]", this, message.getLong("id"), value));
      }
      eventBus.send(inAddress, message);
    }
    for (OutputHook hook : hooks) {
      hook.handleSend(value);
    }
    checkFull();
    return this;
  }

  /**
   * Sends a group start message.
   */
  void doGroupStart(String group, String name, Object args, String parent) {
    checkOpen();
    JsonObject message = createMessage(args)
        .putString("group", group)
        .putString("name", name)
        .putString("parent", parent)
        .putString("action", "startGroup");
    if (open && !paused) {
      if (log.isDebugEnabled()) {
        if (parent != null) {
          log.debug(String.format("%s - Group start: Group[name=%s, group=%s, parent=%s, args=%s]", this, name, group, parent, args));
        } else {
          log.debug(String.format("%s - Group start: Group[name=%s, group=%s, args=%s]", this, name, group, args));
        }
      }
      eventBus.send(inAddress, message);
    }
    checkFull();
  }

  /**
   * Sends a group message.
   */
  void doGroupSend(String group, Object value) {
    checkOpen();
    JsonObject message = createMessage(value)
        .putString("action", "group")
        .putString("group", group);
    if (open && !paused) {
      if (log.isDebugEnabled()) {
        log.debug(String.format("%s - Group send: Group[group=%s, id=%d, message=%s", this, group, message.getLong("id"), value));
      }
      eventBus.send(inAddress, message);
    }
    for (OutputHook hook : hooks) {
      hook.handleSend(value);
    }
    checkFull();
  }

  /**
   * Sends a group end message.
   */
  void doGroupEnd(String group, Object args) {
    checkOpen();
    JsonObject message = createMessage(args)
        .putString("action", "endGroup")
        .putString("group", group);
    if (open && !paused) {
      if (log.isDebugEnabled()) {
        log.debug(String.format("%s - Group end: Group[group=%s, args=%s]", this, group, args));
      }
      eventBus.send(inAddress, message);
    }
    groups.remove(group);
  }

  /**
   * Sends a batch start message.
   */
  void doBatchStart(String batch, Object args) {
    checkOpen();
    JsonObject message = createMessage(args)
        .putString("batch", batch)
        .putString("action", "startBatch");
    if (open && !paused) {
      if (log.isDebugEnabled()) {
        log.debug(String.format("%s - Batch start: Batch[batch=%s]", this, batch));
      }
      eventBus.send(inAddress, message);
    }
    checkFull();
  }

  /**
   * Sends a batch message.
   */
  void doBatchSend(String batch, Object value) {
    checkOpen();
    JsonObject message = createMessage(value)
        .putString("action", "batch")
        .putString("batch", batch);
    if (open && !paused) {
      if (log.isDebugEnabled()) {
        log.debug(String.format("%s - Batch send: Batch[batch=%s, id=%d, message=%s]", this, batch, message.getLong("id"), value));
      }
      eventBus.send(inAddress, message);
    }
    for (OutputHook hook : hooks) {
      hook.handleSend(value);
    }
    checkFull();
  }

  /**
   * Sends a batch end message.
   */
  void doBatchEnd(String batch, Object args) {
    checkOpen();
    JsonObject message = createMessage(args)
        .putString("action", "endBatch")
        .putString("batch", batch);
    if (open && !paused) {
      if (log.isDebugEnabled()) {
        log.debug(String.format("%s - Batch end: Batch[batch=%s, args=%s]", this, batch, args));
      }
      eventBus.send(inAddress, message);
    }
    if (currentBatch != null && currentBatch.id().equals(batch)) {
      currentBatch = null;
    }
  }

  /**
   * Creates a value message.
   */
  private JsonObject createMessage(Object value) {
    // Tag the message with a monotonically increasing ID. The ID
    // will be used by the other side of the connection to guarantee
    // ordering.
    JsonObject message = serializer.serialize(value);
    long id = currentMessage++;
    message.putNumber("id", id);
    messages.put(id, message);
    return message;
  }

  @Override
  public OutputConnection send(final Object message) {
    return doSend(message);
  }

  @Override
  public OutputConnection send(String message) {
    return doSend(message);
  }

  @Override
  public OutputConnection send(Boolean message) {
    return doSend(message);
  }

  @Override
  public OutputConnection send(Character message) {
    return doSend(message);
  }

  @Override
  public OutputConnection send(Short message) {
    return doSend(message);
  }

  @Override
  public OutputConnection send(Integer message) {
    return doSend(message);
  }

  @Override
  public OutputConnection send(Long message) {
    return doSend(message);
  }

  @Override
  public OutputConnection send(Double message) {
    return doSend(message);
  }

  @Override
  public OutputConnection send(Float message) {
    return doSend(message);
  }

  @Override
  public OutputConnection send(JsonObject message) {
    return doSend(message);
  }

  @Override
  public OutputConnection send(JsonArray message) {
    return doSend(message);
  }

  @Override
  public OutputConnection send(Byte message) {
    return doSend(message);
  }

  @Override
  public OutputConnection send(byte[] message) {
    return doSend(message);
  }

  @Override
  public OutputConnection send(Buffer message) {
    return doSend(message);
  }

  @Override
  public String toString() {
    return context.toString();
  }

}
