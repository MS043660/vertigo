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
package net.kuujo.vertigo;

import java.io.IOException;
import java.net.URL;
import java.util.NoSuchElementException;
import java.util.Scanner;

import net.kuujo.vertigo.cluster.Cluster;
import net.kuujo.vertigo.network.ActiveNetwork;
import net.kuujo.vertigo.network.NetworkConfig;

import io.vertx.core.AsyncResult;
import org.vertx.java.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.vertx.java.core.VertxException;
import org.vertx.java.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Container;
import org.vertx.java.platform.Verticle;
import org.vertx.java.platform.VerticleFactory;
import net.kuujo.vertigo.Vertigo;

/**
 * Vertigo network factory.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class NetworkFactory implements VerticleFactory {
  private Vertx vertx;
  private Container container;
  private ClassLoader cl;

  @Override
  public void init(Vertx vertx, Container container, ClassLoader cl) {
    this.vertx = vertx;
    this.container = container;
    this.cl = cl;
  }

  @Override
  public void close() {
    
  }

  @Override
  public Verticle createVerticle(String main) throws Exception {
    JsonObject json = loadJson(main);
    String cluster = json.getString("cluster");
    Vertigo vertigo = new Vertigo(vertx, container);
    NetworkConfig network = vertigo.createNetwork(json);
    Verticle verticle = new NetworkVerticle(vertigo, cluster, network);
    verticle.setVertx(vertx);
    verticle.setContainer(container);
    return verticle;
  }

  /**
   * Loads a network definition from a json network file.
   */
  private JsonObject loadJson(String fileName) {
    URL url = cl.getResource(fileName);
    try {
      try (Scanner scanner = new Scanner(url.openStream(), "UTF-8").useDelimiter("\\A")) {
        String json = scanner.next();
        return new JsonObject(json);
      } catch (NoSuchElementException e) {
        throw new VertxException("Empty network configuration file.");
      } catch (DecodeException e) {
        throw new VertxException("Invalid network configuration file.");
      }
    } catch (IOException e) {
      throw new VertxException("Failed to read network configuration file.");
    }
  }

  @Override
  public void reportException(Logger logger, Throwable t) {
    logger.error("Exception in Java verticle", t);
  }

  /**
   * Deploys a Vertigo network.
   */
  public static class NetworkVerticle extends Verticle {
    private final Vertigo vertigo;
    private final String cluster;
    private final NetworkConfig network;

    public NetworkVerticle(Vertigo vertigo, String cluster, NetworkConfig config) {
      this.vertigo = vertigo;
      this.cluster = cluster;
      this.network = config;
    }

    @Override
    public void start(final Future<Void> startResult) {
      // If no cluster address was provided then deploy the network to a local cluster.
      if (cluster == null) {
        vertigo.deployCluster(new Handler<AsyncResult<Cluster>>() {
          @Override
          public void handle(AsyncResult<Cluster> result) {
            if (result.failed()) {
              startResult.setFailure(result.cause());
            } else {
              result.result().deployNetwork(network, new Handler<AsyncResult<ActiveNetwork>>() {
                @Override
                public void handle(AsyncResult<ActiveNetwork> result) {
                  if (result.failed()) {
                    container.logger().warn(result.cause());
                    startResult.setFailure(result.cause());
                  } else {
                    // Since the cluster is running locally, we don't need to exit.
                    startResult.setResult((Void) null);
                    container.logger().info("Successfully deployed network");
                  }
                }
              });
            }
          }
        });
      } else {
        vertigo.deployNetwork(cluster, network, new Handler<AsyncResult<ActiveNetwork>>() {
          @Override
          public void handle(AsyncResult<ActiveNetwork> result) {
            if (result.failed()) {
              container.logger().warn(result.cause());
              startResult.setFailure(result.cause());
            } else {
              startResult.setResult((Void) null);
              container.logger().info("Successfully deployed network");
              // When deploying the network to a remote cluster, exit the container.
              // The network will be deployed and running on a cluster node.
              container.exit();
            }
          }
        });
      }
    }
  }

}
