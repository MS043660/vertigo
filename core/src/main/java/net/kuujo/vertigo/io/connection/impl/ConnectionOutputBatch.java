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

import net.kuujo.vertigo.io.connection.OutputConnection;

/**
 * Connection level output batch.<p>
 *
 * The connection output batch is an extension of the {@link OutputConnection} that
 * allows batch connections to be selected during routing.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public interface ConnectionOutputBatch extends OutputConnection {

  /**
   * Returns the batch ID.
   *
   * @return The unique batch ID.
   */
  String id();

  /**
   * Ends the batch.
   */
  void end();

  /**
   * Ends the batch.
   *
   * @param args Arguments to the batch's end handler.
   */
  <T> void end(T args);

}
