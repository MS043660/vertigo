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
package net.kuujo.vertigo.io.port.impl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.kuujo.vertigo.hook.ComponentHook;
import net.kuujo.vertigo.hook.OutputHook;
import net.kuujo.vertigo.io.batch.OutputBatch;
import net.kuujo.vertigo.io.batch.impl.BaseOutputBatch;
import net.kuujo.vertigo.io.group.OutputGroup;
import net.kuujo.vertigo.io.group.impl.BaseOutputGroup;
import net.kuujo.vertigo.io.port.OutputPort;
import net.kuujo.vertigo.io.port.OutputPortContext;
import net.kuujo.vertigo.io.stream.OutputStream;
import net.kuujo.vertigo.io.stream.OutputStreamContext;
import net.kuujo.vertigo.io.stream.impl.DefaultOutputStream;
import net.kuujo.vertigo.util.Args;
import net.kuujo.vertigo.util.CountingCompletionHandler;
import net.kuujo.vertigo.util.Observer;
import net.kuujo.vertigo.util.Task;
import net.kuujo.vertigo.util.TaskRunner;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.FutureFactoryImpl;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * Default output port implementation.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class DefaultOutputPort implements OutputPort, Observer<OutputPortContext> {
  private static final Logger log = LoggerFactory.getLogger(DefaultOutputPort.class);
  private static final int DEFAULT_SEND_QUEUE_MAX_SIZE = 10000;
  private final Vertx vertx;
  private OutputPortContext context;
  private final List<OutputStream> streams = new ArrayList<>();
  private List<OutputHook> hooks = new ArrayList<>();
  private final TaskRunner tasks = new TaskRunner();
  private int maxQueueSize = DEFAULT_SEND_QUEUE_MAX_SIZE;
  private Handler<Void> drainHandler;
  private boolean open;

  public DefaultOutputPort(Vertx vertx, OutputPortContext context) {
    this.vertx = vertx;
    this.context = context;
    this.hooks = context.hooks();
    for (Object hook : context.output().instance().component().hooks()) {
      hooks.add((ComponentHook) hook);
    }
    context.registerObserver(this);
  }

  @Override
  public String name() {
    return context.name();
  }

  @Override
  public Vertx vertx() {
    return vertx;
  }

  @Override
  public void update(OutputPortContext context) {
    log.debug(String.format("%s - Out port configuration has changed, updating streams", this));

    // Copy the context in order to ensure that future changes via the
    // observer will not effect this update.
    final OutputPortContext update = context.copy();

    // All updates are run sequentially to prevent race conditions
    // during configuration changes. Without essentially locking the
    // object, it could be possible that connections are simultaneously
    // added and removed or opened and closed on the object.
    tasks.runTask(new Handler<Task>() {
      @Override
      public void handle(final Task task) {
        // Iterate through existing streams and try to determine
        // whether any of them have been removed from the network.
        Iterator<OutputStream> iter = streams.iterator();
        while (iter.hasNext()) {
          final OutputStream stream = iter.next();
          boolean exists = false;
          for (OutputStreamContext output : update.streams()) {
            if (output.address().equals(stream.address())) {
              exists = true;
              break;
            }
          }

          // If a stream was removed from the network, close
          // and remove the connection regardless of whether the
          // close is actually successful.
          if (!exists) {
            stream.close(new Handler<AsyncResult<Void>>() {
              @Override
              public void handle(AsyncResult<Void> result) {
                if (result.failed()) {
                  log.error(String.format("%s - Failed to close output stream: %s", DefaultOutputPort.this, stream));
                } else {
                  log.info(String.format("%s - Closed output stream: %s", DefaultOutputPort.this, stream));
                }
              }
            });
            iter.remove();
          }
        }

        // Now try to determine whether any streams were added to the network.
        final List<OutputStream> newStreams = new ArrayList<>();
        for (OutputStreamContext output : update.streams()) {
          boolean exists = false;
          for (OutputStream stream : streams) {
            if (stream.address().equals(output.address())) {
              exists = true;
              break;
            }
          }
          if (!exists) {
            log.info(String.format("%s - Creating stream: %s", DefaultOutputPort.this, output));
            newStreams.add(new DefaultOutputStream(vertx, output));
          }
        }

        // If the port is open then that means streams have already
        // been set up. We need to add the new streams carefully
        // because it's possible that user code may be sending messages
        // on the port. If messages are sent to a stream that's not
        // yet open then an exception will be thrown.
        if (open) {
          final CountingCompletionHandler<Void> counter = new CountingCompletionHandler<Void>(newStreams.size());
          counter.setHandler(new Handler<AsyncResult<Void>>() {
            @Override
            public void handle(AsyncResult<Void> result) {
              task.complete();
            }
          });

          // Start each stream and add the stream to the streams
          // list only once the stream has been successfully opened.
          // The update lock by the task runner will ensure that we don't
          // accidentally open up two of the same stream even if the
          // configuration is updated again.
          for (final OutputStream stream : newStreams) {
            stream.open(new Handler<AsyncResult<Void>>() {
              @Override
              public void handle(AsyncResult<Void> result) {
                if (result.failed()) {
                  log.error(String.format("%s - Failed to open output stream: %s", DefaultOutputPort.this, stream));
                } else {
                  log.info(String.format("%s - Opened output stream: %s", DefaultOutputPort.this, stream));
                  streams.add(stream);
                }
              }
            });
          }
        } else {
          // If the port's not even open yet then it's okay to just add the
          // connection to the connections list. Once the port is opened it
          // will open the connections.
          for (OutputStream stream : newStreams) {
            streams.add(stream);
          }
          task.complete();
        }
      }
    });
  }

  @Override
  public OutputPort setSendQueueMaxSize(int maxSize) {
    Args.checkPositive(maxSize, "max size must be a positive number");
    this.maxQueueSize = maxSize;
    for (OutputStream stream : streams) {
      stream.setSendQueueMaxSize(maxQueueSize);
    }
    return this;
  }

  @Override
  public int getSendQueueMaxSize() {
    return maxQueueSize;
  }

  @Override
  public int size() {
    int highest = 0;
    for (OutputStream stream : streams) {
      highest = Math.max(highest, stream.size());
    }
    return highest;
  }

  @Override
  public boolean sendQueueFull() {
    for (OutputStream stream : streams) {
      if (stream.sendQueueFull()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public OutputPort drainHandler(Handler<Void> handler) {
    this.drainHandler = handler;
    for (OutputStream stream : streams) {
      stream.drainHandler(handler);
    }
    return this;
  }

  @Override
  public OutputPort open() {
    return open(null);
  }

  @Override
  public OutputPort open(final Handler<AsyncResult<Void>> doneHandler) {
    // Prevent the object from being opened and closed simultaneously
    // by queueing open/close operations as tasks.
    tasks.runTask(new Handler<Task>() {
      @Override
      public void handle(final Task task) {
        if (!open) {
          streams.clear();
          open = true;
          final CountingCompletionHandler<Void> counter = new CountingCompletionHandler<Void>(context.streams().size());
          counter.setHandler(new Handler<AsyncResult<Void>>() {
            @Override
            public void handle(AsyncResult<Void> result) {
              if (doneHandler != null) {
                doneHandler.handle(result);
              }
              task.complete();
            }
          });

          // Only add streams to the stream list once the stream has been
          // opened. This helps ensure that we don't attempt to send messages
          // on a closed stream.
          for (OutputStreamContext output : context.streams()) {
            final OutputStream stream = new DefaultOutputStream(vertx, output);
            stream.setSendQueueMaxSize(maxQueueSize);
            stream.drainHandler(drainHandler);
            stream.open(new Handler<AsyncResult<Void>>() {
              @Override
              public void handle(AsyncResult<Void> result) {
                if (result.failed()) {
                  log.error(String.format("%s - Failed to open output stream: %s", DefaultOutputPort.this, stream));
                  counter.fail(result.cause());
                } else {
                  log.info(String.format("%s - Opened output stream: %s", DefaultOutputPort.this, stream));
                  streams.add(stream);
                  counter.succeed();
                }
              }
            });
          }
        } else {
          new FutureFactoryImpl().succeededFuture((Void) null).setHandler(doneHandler);
          task.complete();
        }
      }
    });
    return this;
  }

  @Override
  public void close() {
    close(null);
  }

  @Override
  public void close(final Handler<AsyncResult<Void>> doneHandler) {
    // Prevent the object from being opened and closed simultaneously
    // by queueing open/close operations as tasks.
    tasks.runTask(new Handler<Task>() {
      @Override
      public void handle(final Task task) {
        if (open) {
          List<OutputStream> streams = new ArrayList<>(DefaultOutputPort.this.streams);
          DefaultOutputPort.this.streams.clear();
          open = false;
          final CountingCompletionHandler<Void> counter = new CountingCompletionHandler<Void>(streams.size());
          counter.setHandler(new Handler<AsyncResult<Void>>() {
            @Override
            public void handle(AsyncResult<Void> result) {
              if (doneHandler != null) {
                doneHandler.handle(result);
              }
              task.complete();
            }
          });
          for (final OutputStream stream : streams) {
            stream.close(new Handler<AsyncResult<Void>>() {
              @Override
              public void handle(AsyncResult<Void> result) {
                if (result.failed()) {
                  log.warn(String.format("%s - Failed to close output stream: %s", DefaultOutputPort.this, stream));
                  counter.fail(result.cause());
                } else {
                  log.info(String.format("%s - Closed output stream: %s", DefaultOutputPort.this, stream));
                  counter.succeed();
                }
              }
            });
          }
        } else {
        	new FutureFactoryImpl().succeededFuture((Void) null).setHandler(doneHandler);
          task.complete();
        }
      }
    });
  }

  @Override
  public OutputPort batch(Handler<OutputBatch> handler) {
    return batch(UUID.randomUUID().toString(), null, handler);
  }

  @Override
  public OutputPort batch(Object args, Handler<OutputBatch> handler) {
    return batch(UUID.randomUUID().toString(), args, handler);
  }

  @Override
  public OutputPort batch(final String id, final Object args, final Handler<OutputBatch> handler) {
    final List<OutputBatch> batches = new ArrayList<>();
    final int streamsSize = streams.size();
    if (streamsSize == 0) {
      handler.handle(new BaseOutputBatch(id, vertx, batches));
    } else {
      for (OutputStream stream : streams) {
        stream.batch(id, args, new Handler<OutputBatch>() {
          @Override
          public void handle(OutputBatch batch) {
            batches.add(batch);
            if (batches.size() == streamsSize) {
              handler.handle(new BaseOutputBatch(id, vertx, batches));
            }
          }
        });
      }
    }
    return this;
  }

  @Override
  public OutputPort group(Handler<OutputGroup> handler) {
    return group(UUID.randomUUID().toString(), null, handler);
  }

  @Override
  public OutputPort group(String name, Handler<OutputGroup> handler) {
    return group(name, null, handler);
  }

  @Override
  public OutputPort group(final String name, final Object args, final Handler<OutputGroup> handler) {
    final List<OutputGroup> groups = new ArrayList<>();
    final int streamsSize = streams.size();
    if (streamsSize == 0) {
      handler.handle(new BaseOutputGroup(name, vertx, groups));
    } else {
      for (OutputStream stream : streams) {
        stream.group(name, args, new Handler<OutputGroup>() {
          @Override
          public void handle(OutputGroup group) {
            groups.add(group);
            if (groups.size() == streamsSize) {
              handler.handle(new BaseOutputGroup(name, vertx, groups));
            }
          }
        });
      }
    }
    return this;
  }

  /**
   * Triggers send hooks.
   */
  private void triggerSend(Object message) {
    for (OutputHook hook : hooks) {
      hook.handleSend(message);
    }
  }

  @Override
  public OutputPort send(Object message) {
    for (OutputStream stream : streams) {
      stream.send(message);
    }
    triggerSend(message);
    return this;
  }

  @Override
  public OutputPort send(String message) {
    for (OutputStream stream : streams) {
      stream.send(message);
    }
    triggerSend(message);
    return this;
  }

  @Override
  public OutputPort send(Boolean message) {
    for (OutputStream stream : streams) {
      stream.send(message);
    }
    triggerSend(message);
    return this;
  }

  @Override
  public OutputPort send(Character message) {
    for (OutputStream stream : streams) {
      stream.send(message);
    }
    triggerSend(message);
    return this;
  }

  @Override
  public OutputPort send(Short message) {
    for (OutputStream stream : streams) {
      stream.send(message);
    }
    triggerSend(message);
    return this;
  }

  @Override
  public OutputPort send(Integer message) {
    for (OutputStream stream : streams) {
      stream.send(message);
    }
    triggerSend(message);
    return this;
  }

  @Override
  public OutputPort send(Long message) {
    for (OutputStream stream : streams) {
      stream.send(message);
    }
    triggerSend(message);
    return this;
  }

  @Override
  public OutputPort send(Double message) {
    for (OutputStream stream : streams) {
      stream.send(message);
    }
    triggerSend(message);
    return this;
  }

  @Override
  public OutputPort send(Float message) {
    for (OutputStream stream : streams) {
      stream.send(message);
    }
    triggerSend(message);
    return this;
  }

  @Override
  public OutputPort send(JsonObject message) {
    for (OutputStream stream : streams) {
      stream.send(message);
    }
    triggerSend(message);
    return this;
  }

  @Override
  public OutputPort send(JsonArray message) {
    for (OutputStream stream : streams) {
      stream.send(message);
    }
    triggerSend(message);
    return this;
  }

  @Override
  public OutputPort send(Byte message) {
    for (OutputStream stream : streams) {
      stream.send(message);
    }
    triggerSend(message);
    return this;
  }

  @Override
  public OutputPort send(byte[] message) {
    for (OutputStream stream : streams) {
      stream.send(message);
    }
    triggerSend(message);
    return this;
  }

  @Override
  public OutputPort send(Buffer message) {
    for (OutputStream stream : streams) {
      stream.send(message);
    }
    triggerSend(message);
    return this;
  }

  @Override
  public String toString() {
    return context.toString();
  }

}
