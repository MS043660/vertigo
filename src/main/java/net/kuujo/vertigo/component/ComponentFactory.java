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
package net.kuujo.vertigo.component;

import org.vertx.java.core.Vertx;
import org.vertx.java.platform.Container;

import net.kuujo.vertigo.aggregator.Aggregator;
import net.kuujo.vertigo.context.InstanceContext;
import net.kuujo.vertigo.feeder.Feeder;
import net.kuujo.vertigo.filter.Filter;
import net.kuujo.vertigo.rpc.Executor;
import net.kuujo.vertigo.splitter.Splitter;
import net.kuujo.vertigo.worker.Worker;

/**
 * A component instance factory.
 *
 * @author Jordan Halterman
 */
public interface ComponentFactory {

  /**
   * Sets the factory Vertx instance.
   *
   * @param vertx
   *   A Vertx instance.
   * @return
   *   The called factory instance.
   */
  ComponentFactory setVertx(Vertx vertx);

  /**
   * Sets the factory Container instance.
   *
   * @param container
   *   A Vert.x container.
   * @return
   *   The called factory instance.
   */
  ComponentFactory setContainer(Container container);

  /**
   * Creates a feeder.
   *
   * @param context
   *   The feeder instance context.
   * @return
   *   A new feeder instance.
   */
  Feeder createFeeder(InstanceContext context);

  /**
   * Creates an executor.
   *
   * @param context
   *   The executor instance context.
   * @return
   *   A new executor instance.
   */
  Executor createExecutor(InstanceContext context);

  /**
   * Creates a worker.
   *
   * @param context
   *   The worker instance context.
   * @return
   *   A new worker instance.
   */
  Worker createWorker(InstanceContext context);

  /**
   * Creates a filter.
   *
   * @param context
   *   The filter instance context.
   * @return
   *   A new filter instance.
   */
  Filter createFilter(InstanceContext context);

  /**
   * Creates a splitter.
   *
   * @param context
   *   The splitter instance context.
   * @return
   *   A new splitter instance.
   */
  Splitter createSplitter(InstanceContext context);

  /**
   * Creates an aggregator.
   *
   * @param context
   *   The aggregator instance context.
   * @return
   *   A new aggregator instance.
   */
  <T> Aggregator<T> createAggregator(InstanceContext context);

}