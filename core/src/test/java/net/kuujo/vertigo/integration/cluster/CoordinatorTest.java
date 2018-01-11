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
package net.kuujo.vertigo.integration.cluster;

import static org.vertx.testtools.VertxAssert.assertTrue;
import static org.vertx.testtools.VertxAssert.fail;
import static org.vertx.testtools.VertxAssert.testComplete;
import net.kuujo.vertigo.Vertigo;
import net.kuujo.vertigo.cluster.Cluster;
import net.kuujo.vertigo.cluster.data.WatchableAsyncMap;
import net.kuujo.vertigo.cluster.data.impl.WrappedWatchableAsyncMap;
import net.kuujo.vertigo.cluster.impl.DefaultCluster;
import net.kuujo.vertigo.component.ComponentCoordinator;
import net.kuujo.vertigo.component.InstanceContext;
import net.kuujo.vertigo.component.impl.DefaultComponentCoordinator;
import net.kuujo.vertigo.component.impl.DefaultInstanceContext;
import net.kuujo.vertigo.component.impl.DefaultVerticleContext;
import net.kuujo.vertigo.io.impl.DefaultInputContext;
import net.kuujo.vertigo.io.impl.DefaultOutputContext;
import net.kuujo.vertigo.network.NetworkContext;
import net.kuujo.vertigo.network.impl.DefaultNetworkContext;
import net.kuujo.vertigo.util.Contexts;

import org.junit.Test;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import org.vertx.testtools.TestVerticle;

/**
 * A component coordinator test.
 *
 * @author Jordan Halterman
 */
public class CoordinatorTest extends TestVerticle {

  @Test
  public void testStartWithExistingContext() {
    final Vertigo vertigo = new Vertigo(this);
    vertigo.deployCluster("vertigo", new Handler<AsyncResult<Cluster>>() {
      @Override
      public void handle(AsyncResult<Cluster> result) {
        assertTrue(result.succeeded());
        final NetworkContext context = DefaultNetworkContext.Builder.newBuilder()
            .setName("test-coordinator-start")
            .setAddress("test")
            .setStatusAddress("test.__status")
            .setCluster("test")
            .addComponent(DefaultVerticleContext.Builder.newBuilder()
                .setName("test")
                .setAddress("test.test")
                .setStatusAddress("test.test.__status")
                .addInstance(DefaultInstanceContext.Builder.newBuilder()
                    .setAddress("test.test-1")
                    .setStatusAddress("test.test-1.__status")
                    .setInput(DefaultInputContext.Builder.newBuilder().build())
                    .setOutput(DefaultOutputContext.Builder.newBuilder().build()).build()).build()).build();
        final InstanceContext instance = context.component("test").instances().iterator().next();

        final Cluster cluster = new DefaultCluster("vertigo", vertx, container);
        final WatchableAsyncMap<String, String> data = new WrappedWatchableAsyncMap<String, String>(cluster.<String, String>getMap("test"), vertx);

        data.put(instance.address(), Contexts.serialize(instance).encode(), new Handler<AsyncResult<String>>() {
          @Override
          public void handle(AsyncResult<String> result) {
            if (result.failed()) {
              fail(result.cause().getMessage());
            } else {
              final ComponentCoordinator coordinator = new DefaultComponentCoordinator(instance, vertx, new DefaultCluster("vertigo", vertx, container));
              coordinator.start(new Handler<AsyncResult<Void>>() {
                @Override
                public void handle(AsyncResult<Void> result) {
                  if (result.failed()) {
                    fail(result.cause().getMessage());
                  } else {
                    testComplete();
                  }
                }
              });
              data.put(context.status(), "ready");
            }
          }
        });
      }
    });
  }

  @Test
  public void testPauseHandler() {
    final Vertigo vertigo = new Vertigo(this);
    vertigo.deployCluster("vertigo", new Handler<AsyncResult<Cluster>>() {
      @Override
      public void handle(AsyncResult<Cluster> result) {
        assertTrue(result.succeeded());
        final NetworkContext context = DefaultNetworkContext.Builder.newBuilder()
            .setName("test-coordinator-pause")
            .setAddress("test")
            .setStatusAddress("test.__status")
            .setCluster("test")
            .addComponent(DefaultVerticleContext.Builder.newBuilder()
                .setName("test")
                .setAddress("test.test")
                .setStatusAddress("test.test.__status")
                .addInstance(DefaultInstanceContext.Builder.newBuilder()
                    .setAddress("test.test-1")
                    .setStatusAddress("test.test-1.__status")
                    .setInput(DefaultInputContext.Builder.newBuilder().build())
                    .setOutput(DefaultOutputContext.Builder.newBuilder().build()).build()).build()).build();
        final InstanceContext instance = context.component("test").instances().iterator().next();

        final Cluster cluster = new DefaultCluster("vertigo", vertx, container);
        final WatchableAsyncMap<String, String> data = new WrappedWatchableAsyncMap<String, String>(cluster.<String, String>getMap("test"), vertx);

        data.put(instance.address(), Contexts.serialize(instance).encode(), new Handler<AsyncResult<String>>() {
          @Override
          public void handle(AsyncResult<String> result) {
            if (result.failed()) {
              fail(result.cause().getMessage());
            } else {
              final ComponentCoordinator coordinator = new DefaultComponentCoordinator(instance, vertx, new DefaultCluster("vertigo", vertx, container));
              coordinator.start(new Handler<AsyncResult<Void>>() {
                @Override
                public void handle(AsyncResult<Void> result) {
                  if (result.failed()) {
                    fail(result.cause().getMessage());
                  } else {
                    coordinator.pauseHandler(new Handler<Void>() {
                      @Override
                      public void handle(Void _) {
                        testComplete();
                      }
                    });
                    data.remove(context.status());
                  }
                }
              });
              data.put(context.status(), "ready");
            }
          }
        });
      }
    });
  }

  @Test
  public void testResumeHandler() {
    final Vertigo vertigo = new Vertigo(this);
    vertigo.deployCluster("vertigo", new Handler<AsyncResult<Cluster>>() {
      @Override
      public void handle(AsyncResult<Cluster> result) {
        assertTrue(result.succeeded());
        final NetworkContext context = DefaultNetworkContext.Builder.newBuilder()
            .setName("test-coordinator-resume")
            .setAddress("test")
            .setStatusAddress("test.__status")
            .setCluster("test")
            .addComponent(DefaultVerticleContext.Builder.newBuilder()
                .setName("test")
                .setAddress("test.test")
                .setStatusAddress("test.test.__status")
                .addInstance(DefaultInstanceContext.Builder.newBuilder()
                    .setAddress("test.test-1")
                    .setStatusAddress("test.test-1.__status")
                    .setInput(DefaultInputContext.Builder.newBuilder().build())
                    .setOutput(DefaultOutputContext.Builder.newBuilder().build()).build()).build()).build();
        final InstanceContext instance = context.component("test").instances().iterator().next();

        final Cluster cluster = new DefaultCluster("vertigo", vertx, container);
        final WatchableAsyncMap<String, String> data = new WrappedWatchableAsyncMap<String, String>(cluster.<String, String>getMap("test"), vertx);

        data.put(instance.address(), Contexts.serialize(instance).encode(), new Handler<AsyncResult<String>>() {
          @Override
          public void handle(AsyncResult<String> result) {
            if (result.failed()) {
              fail(result.cause().getMessage());
            } else {
              final ComponentCoordinator coordinator = new DefaultComponentCoordinator(instance, vertx, new DefaultCluster("vertigo", vertx, container));
              coordinator.start(new Handler<AsyncResult<Void>>() {
                @Override
                public void handle(AsyncResult<Void> result) {
                  if (result.failed()) {
                    fail(result.cause().getMessage());
                  } else {
                    coordinator.resumeHandler(new Handler<Void>() {
                      @Override
                      public void handle(Void _) {
                        testComplete();
                      }
                    });
                    data.remove(context.status(), new Handler<AsyncResult<String>>() {
                      @Override
                      public void handle(AsyncResult<String> result) {
                        if (result.failed()) {
                          fail(result.cause().getMessage());
                        } else {
                          data.put(context.status(), "ready");
                        }
                      }
                    });
                  }
                }
              });
              data.put(context.status(), "ready");
            }
          }
        });
      }
    });
  }

}
