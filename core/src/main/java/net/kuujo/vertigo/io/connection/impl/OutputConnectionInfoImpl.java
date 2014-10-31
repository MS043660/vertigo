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

import net.kuujo.vertigo.hook.OutputHook;
import net.kuujo.vertigo.io.connection.OutputConnectionInfo;
import net.kuujo.vertigo.io.stream.OutputStreamInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Output connection info implementation.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class OutputConnectionInfoImpl extends BaseConnectionInfoImpl<OutputConnectionInfo> implements OutputConnectionInfo {
  private OutputStreamInfo stream;
  private List<OutputHook> hooks = new ArrayList<>();

  @Override
  public OutputStreamInfo stream() {
    return stream;
  }

  @Override
  public List<OutputHook> hooks() {
    return hooks;
  }

}