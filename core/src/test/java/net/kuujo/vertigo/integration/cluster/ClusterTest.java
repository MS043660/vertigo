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

import static org.vertx.testtools.VertxAssert.assertNotNull;
import static org.vertx.testtools.VertxAssert.assertTrue;
import static org.vertx.testtools.VertxAssert.testComplete;
import net.kuujo.vertigo.Vertigo;
import net.kuujo.vertigo.cluster.Cluster;

import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.model.InitializationError;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import org.vertx.java.platform.Verticle;
import org.vertx.testtools.JavaClassRunner;
import org.vertx.testtools.TestVerticle;

/**
 * Cluster deployment tests.
 *
 * @author Jordan Halterman
 */
@RunWith(ClusterTest.ClusterClassRunner.class)
public class ClusterTest extends TestVerticle {

  public static class ClusterClassRunner extends JavaClassRunner {
    static {
      System.setProperty("vertx.mods", "src/test/resources/test-mods");
    }
    public ClusterClassRunner(Class<?> klass) throws InitializationError {
      super(klass);
    }
  }

  public static class TestVerticle1 extends Verticle {
    @Override
    public void start() {
      super.start();
    }
  }

  public static class TestVerticle2 extends Verticle {
    @Override
    public void start() {
      testComplete();
    }
  }

  @Test
  public void testDeployVerticle() {
    Vertigo vertigo = new Vertigo(this);
    vertigo.deployCluster("test", new Handler<AsyncResult<Cluster>>() {
      @Override
      public void handle(AsyncResult<Cluster> result) {
        assertTrue(result.succeeded());
        final Cluster cluster = result.result();
        cluster.deployVerticle(TestVerticle2.class.getName(), new JsonObject().putString("foo", "bar"), 1, new Handler<AsyncResult<String>>() {
          @Override
          public void handle(AsyncResult<String> result) {
            assertTrue(result.succeeded());
            assertNotNull(result.result());
          }
        });
      }
    });
  }

  @Test
  public void testUndeployVerticle() {
    Vertigo vertigo = new Vertigo(this);
    vertigo.deployCluster("test", new Handler<AsyncResult<Cluster>>() {
      @Override
      public void handle(AsyncResult<Cluster> result) {
        assertTrue(result.succeeded());
        final Cluster cluster = result.result();
        cluster.deployVerticle(TestVerticle1.class.getName(), new JsonObject().putString("foo", "bar"), 1, new Handler<AsyncResult<String>>() {
          @Override
          public void handle(AsyncResult<String> result) {
            assertTrue(result.succeeded());
            assertNotNull(result.result());
            cluster.undeployVerticle(result.result(), new Handler<AsyncResult<Void>>() {
              @Override
              public void handle(AsyncResult<Void> result) {
                assertTrue(result.succeeded());
                testComplete();
              }
            });
          }
        });
      }
    });
  }

  @Test
  public void testDeployWorkerVerticle() {
    Vertigo vertigo = new Vertigo(this);
    vertigo.deployCluster("test", new Handler<AsyncResult<Cluster>>() {
      @Override
      public void handle(AsyncResult<Cluster> result) {
        assertTrue(result.succeeded());
        final Cluster cluster = result.result();
        cluster.deployWorkerVerticle(TestVerticle2.class.getName(), new JsonObject().putString("foo", "bar"), 1, false, new Handler<AsyncResult<String>>() {
          @Override
          public void handle(AsyncResult<String> result) {
            assertTrue(result.succeeded());
            assertNotNull(result.result());
          }
        });
      }
    });
  }

  @Test
  public void testUndeployWorkerVerticle() {
    Vertigo vertigo = new Vertigo(this);
    vertigo.deployCluster("test", new Handler<AsyncResult<Cluster>>() {
      @Override
      public void handle(AsyncResult<Cluster> result) {
        assertTrue(result.succeeded());
        final Cluster cluster = result.result();
        cluster.deployWorkerVerticle(TestVerticle1.class.getName(), new JsonObject().putString("foo", "bar"), 1, false, new Handler<AsyncResult<String>>() {
          @Override
          public void handle(AsyncResult<String> result) {
            assertTrue(result.succeeded());
            assertNotNull(result.result());
            cluster.undeployVerticle(result.result(), new Handler<AsyncResult<Void>>() {
              @Override
              public void handle(AsyncResult<Void> result) {
                assertTrue(result.succeeded());
                testComplete();
              }
            });
          }
        });
      }
    });
  }

  @Test
  public void testDeployModule() {
    Vertigo vertigo = new Vertigo(this);
    vertigo.deployCluster("test", new Handler<AsyncResult<Cluster>>() {
      @Override
      public void handle(AsyncResult<Cluster> result) {
        assertTrue(result.succeeded());
        final Cluster cluster = result.result();
        cluster.deployModule("net.kuujo~test-mod-2~1.0", new Handler<AsyncResult<String>>() {
          @Override
          public void handle(AsyncResult<String> result) {
            assertTrue(result.succeeded());
          }
        });
      }
    });
  }

  @Test
  public void testUndeployModule() {
    Vertigo vertigo = new Vertigo(this);
    vertigo.deployCluster("test", new Handler<AsyncResult<Cluster>>() {
      @Override
      public void handle(AsyncResult<Cluster> result) {
        assertTrue(result.succeeded());
        final Cluster cluster = result.result();
        cluster.deployModule("net.kuujo~test-mod-1~1.0", new Handler<AsyncResult<String>>() {
          @Override
          public void handle(AsyncResult<String> result) {
            assertTrue(result.succeeded());
            assertNotNull(result.result());
            cluster.undeployModule(result.result(), new Handler<AsyncResult<Void>>() {
              @Override
              public void handle(AsyncResult<Void> result) {
                assertTrue(result.succeeded());
                testComplete();
              }
            });
          }
        });
      }
    });
  }

  @AfterClass
  public static void after() {
    System.clearProperty("vertx.mods");
  }

}
