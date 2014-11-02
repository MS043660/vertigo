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
package net.kuujo.vertigo.util;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;

/**
 * A running task.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class Task {
  private static final Logger log = LoggerFactory.getLogger(Task.class);
  private final TaskRunner runner;

  public Task(TaskRunner runner) {
    this.runner = runner;
  }

  /**
   * Fails the task.
   */
  public void fail(Throwable t) {
    log.error(t);
    runner.complete(this);
  }

  /**
   * Completes the task.
   */
  public void complete() {
    runner.complete(this);
  }

}