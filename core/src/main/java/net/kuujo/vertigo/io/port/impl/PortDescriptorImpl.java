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

import io.vertx.core.ServiceHelper;
import io.vertx.core.json.JsonObject;
import net.kuujo.vertigo.io.port.PortDescriptor;
import net.kuujo.vertigo.spi.PortTypeResolver;

/**
 * Port descriptor implementation.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class PortDescriptorImpl implements PortDescriptor {
  private final String name;
  private final Class<?> type;

  public PortDescriptorImpl(PortDescriptor port) {
    this.name = port.name();
    this.type = port.type();
  }

  public PortDescriptorImpl(JsonObject port) {
    this.name = port.getString("name");
    String type = port.getString("type");
    this.type = type != null ? resolver.resolve(type) : Object.class;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public Class<?> type() {
    return type;
  }

  static PortTypeResolver resolver = ServiceHelper.loadFactory(PortTypeResolver.class);

}