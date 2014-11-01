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
package net.kuujo.vertigo.io.stream;

import io.vertx.codegen.annotations.VertxGen;
import net.kuujo.vertigo.TypeInfo;
import net.kuujo.vertigo.io.connection.OutputConnectionInfo;
import net.kuujo.vertigo.io.port.OutputPortInfo;
import net.kuujo.vertigo.io.partitioner.Partitioner;
import net.kuujo.vertigo.io.stream.impl.OutputStreamInfoImpl;

import java.util.List;

/**
 * The output stream context represents a set of output connections
 * from one component instance to all instances of another component.
 * The context contains information about how to dispatch messages
 * between the group of target component instances.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
@VertxGen
public interface OutputStreamInfo extends TypeInfo<OutputStreamInfo> {

  /**
   * Returns a new output stream info builder.
   *
   * @return A new output stream info builder.
   */
  static TypeInfo.Builder<OutputStreamInfo> builder() {
    return new OutputStreamInfoImpl.Builder();
  }

  /**
   * Returns a new output stream info builder.
   *
   * @param stream An existing output stream info object to wrap.
   * @return An output stream builder wrapper.
   */
  static TypeInfo.Builder<OutputStreamInfo> builder(OutputStreamInfo stream) {
    return new OutputStreamInfoImpl.Builder((OutputStreamInfoImpl) stream);
  }

  /**
   * Returns the parent output port context.
   *
   * @return The parent port context.
   */
  OutputPortInfo port();

  /**
   * Returns the stream connection partitioner.
   *
   * @return The stream connection partitioner.
   */
  Partitioner partitioner();

  /**
   * Returns a list of output connections.
   *
   * @return A list of output connections.
   */
  List<OutputConnectionInfo> connections();

}
