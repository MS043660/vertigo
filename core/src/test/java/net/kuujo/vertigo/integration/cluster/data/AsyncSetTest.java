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
package net.kuujo.vertigo.integration.cluster.data;

import static org.vertx.testtools.VertxAssert.assertFalse;
import static org.vertx.testtools.VertxAssert.assertTrue;
import static org.vertx.testtools.VertxAssert.testComplete;
import net.kuujo.vertigo.Vertigo;
import net.kuujo.vertigo.cluster.Cluster;
import net.kuujo.vertigo.cluster.data.AsyncSet;
import net.kuujo.vertigo.cluster.impl.DefaultCluster;

import org.junit.Test;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import org.vertx.testtools.TestVerticle;

/**
 * Asynchronous set tests.
 *
 * @author Jordan Halterman
 */
public class AsyncSetTest extends TestVerticle {

  @Test
  public void testSetAdd() {
    Vertigo vertigo = new Vertigo(this);
    vertigo.deployCluster("test", new Handler<AsyncResult<Cluster>>() {
      @Override
      public void handle(AsyncResult<Cluster> result) {
        assertTrue(result.succeeded());
        final Cluster cluster = new DefaultCluster("test", vertx, container);
        final AsyncSet<String> data = cluster.getSet("test-set-add");
        data.add("foo", new Handler<AsyncResult<Boolean>>() {
          @Override
          public void handle(AsyncResult<Boolean> result) {
            assertTrue(result.succeeded());
            assertTrue(result.result());
            data.add("foo", new Handler<AsyncResult<Boolean>>() {
              @Override
              public void handle(AsyncResult<Boolean> result) {
                assertTrue(result.succeeded());
                assertFalse(result.result());
                testComplete();
              }
            });
          }
        });
      }
    });
  }

  @Test
  public void testSetContains() {
    Vertigo vertigo = new Vertigo(this);
    vertigo.deployCluster("test", new Handler<AsyncResult<Cluster>>() {
      @Override
      public void handle(AsyncResult<Cluster> result) {
        assertTrue(result.succeeded());
        final Cluster cluster = new DefaultCluster("test", vertx, container);
        final AsyncSet<String> data = cluster.getSet("test-set-contains");
        data.contains("foo", new Handler<AsyncResult<Boolean>>() {
          @Override
          public void handle(AsyncResult<Boolean> result) {
            assertTrue(result.succeeded());
            assertFalse(result.result());
            data.add("foo", new Handler<AsyncResult<Boolean>>() {
              @Override
              public void handle(AsyncResult<Boolean> result) {
                assertTrue(result.succeeded());
                assertTrue(result.result());
                data.contains("foo", new Handler<AsyncResult<Boolean>>() {
                  @Override
                  public void handle(AsyncResult<Boolean> result) {
                    assertTrue(result.succeeded());
                    assertTrue(result.result());
                    testComplete();
                  }
                });
              }
            });
          }
        });
      }
    });
  }

  @Test
  public void testSetRemove() {
    Vertigo vertigo = new Vertigo(this);
    vertigo.deployCluster("test", new Handler<AsyncResult<Cluster>>() {
      @Override
      public void handle(AsyncResult<Cluster> result) {
        assertTrue(result.succeeded());
        final Cluster cluster = new DefaultCluster("test", vertx, container);
        final AsyncSet<String> data = cluster.getSet("test-set-remove");
        data.add("foo", new Handler<AsyncResult<Boolean>>() {
          @Override
          public void handle(AsyncResult<Boolean> result) {
            assertTrue(result.succeeded());
            assertTrue(result.result());
            data.remove("foo", new Handler<AsyncResult<Boolean>>() {
              @Override
              public void handle(AsyncResult<Boolean> result) {
                assertTrue(result.succeeded());
                assertTrue(result.result());
                data.remove("foo", new Handler<AsyncResult<Boolean>>() {
                  @Override
                  public void handle(AsyncResult<Boolean> result) {
                    assertTrue(result.succeeded());
                    assertFalse(result.result());
                    testComplete();
                  }
                });
              }
            });
          }
        });
      }
    });
  }

  @Test
  public void testSetSize() {
    Vertigo vertigo = new Vertigo(this);
    vertigo.deployCluster("test", new Handler<AsyncResult<Cluster>>() {
      @Override
      public void handle(AsyncResult<Cluster> result) {
        assertTrue(result.succeeded());
        final Cluster cluster = new DefaultCluster("test", vertx, container);
        final AsyncSet<String> data = cluster.getSet("test-set-size");
        data.add("foo", new Handler<AsyncResult<Boolean>>() {
          @Override
          public void handle(AsyncResult<Boolean> result) {
            assertTrue(result.succeeded());
            assertTrue(result.result());
            data.add("bar", new Handler<AsyncResult<Boolean>>() {
              @Override
              public void handle(AsyncResult<Boolean> result) {
                assertTrue(result.succeeded());
                assertTrue(result.result());
                data.add("baz", new Handler<AsyncResult<Boolean>>() {
                  @Override
                  public void handle(AsyncResult<Boolean> result) {
                    assertTrue(result.succeeded());
                    assertTrue(result.result());
                    data.size(new Handler<AsyncResult<Integer>>() {
                      @Override
                      public void handle(AsyncResult<Integer> result) {
                        assertTrue(result.succeeded());
                        assertTrue(result.result() == 3);
                        testComplete();
                      }
                    });
                  }
                });
              }
            });
          }
        });
      }
    });
  }

  @Test
  public void testSetClear() {
    Vertigo vertigo = new Vertigo(this);
    vertigo.deployCluster("test", new Handler<AsyncResult<Cluster>>() {
      @Override
      public void handle(AsyncResult<Cluster> result) {
        assertTrue(result.succeeded());
        final Cluster cluster = new DefaultCluster("test", vertx, container);
        final AsyncSet<String> data = cluster.getSet("test-set-clear");
        data.add("foo", new Handler<AsyncResult<Boolean>>() {
          @Override
          public void handle(AsyncResult<Boolean> result) {
            assertTrue(result.succeeded());
            assertTrue(result.result());
            data.add("bar", new Handler<AsyncResult<Boolean>>() {
              @Override
              public void handle(AsyncResult<Boolean> result) {
                assertTrue(result.succeeded());
                assertTrue(result.result());
                data.add("baz", new Handler<AsyncResult<Boolean>>() {
                  @Override
                  public void handle(AsyncResult<Boolean> result) {
                    assertTrue(result.succeeded());
                    assertTrue(result.result());
                    data.size(new Handler<AsyncResult<Integer>>() {
                      @Override
                      public void handle(AsyncResult<Integer> result) {
                        assertTrue(result.succeeded());
                        assertTrue(result.result() == 3);
                        data.clear(new Handler<AsyncResult<Void>>() {
                          @Override
                          public void handle(AsyncResult<Void> result) {
                            assertTrue(result.succeeded());
                            data.size(new Handler<AsyncResult<Integer>>() {
                              @Override
                              public void handle(AsyncResult<Integer> result) {
                                assertTrue(result.succeeded());
                                assertTrue(result.result() == 0);
                                testComplete();
                              }
                            });
                          }
                        });
                      }
                    });
                  }
                });
              }
            });
          }
        });
      }
    });
  }

  @Test
  public void testSetIsEmpty() {
    Vertigo vertigo = new Vertigo(this);
    vertigo.deployCluster("test", new Handler<AsyncResult<Cluster>>() {
      @Override
      public void handle(AsyncResult<Cluster> result) {
        assertTrue(result.succeeded());
        final Cluster cluster = new DefaultCluster("test", vertx, container);
        final AsyncSet<String> data = cluster.getSet("test-set-is-empty");
        data.isEmpty(new Handler<AsyncResult<Boolean>>() {
          @Override
          public void handle(AsyncResult<Boolean> result) {
            assertTrue(result.succeeded());
            assertTrue(result.result());
            data.add("foo", new Handler<AsyncResult<Boolean>>() {
              @Override
              public void handle(AsyncResult<Boolean> result) {
                assertTrue(result.succeeded());
                assertTrue(result.result());
                data.isEmpty(new Handler<AsyncResult<Boolean>>() {
                  @Override
                  public void handle(AsyncResult<Boolean> result) {
                    assertTrue(result.succeeded());
                    assertFalse(result.result());
                    testComplete();
                  }
                });
              }
            });
          }
        });
      }
    });
  }

}
