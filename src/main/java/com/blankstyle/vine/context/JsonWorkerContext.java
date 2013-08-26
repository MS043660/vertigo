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
package com.blankstyle.vine.context;

import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonObject;

/**
 * A JSON based worker context.
 *
 * @author Jordan Halterman
 */
public class JsonWorkerContext implements WorkerContext<JsonObject> {

  private JsonObject context = new JsonObject();

  private SeedContext<JsonObject> parent;

  private Handler<WorkerContext<JsonObject>> updateHandler;

  public JsonWorkerContext() {
  }

  public JsonWorkerContext(String name) {
    context.putString("name", name);
  }

  public JsonWorkerContext(JsonObject json) {
    context = json;
  }

  public JsonWorkerContext(JsonObject json, SeedContext<JsonObject> parent) {
    this(json);
    this.parent = parent;
  }

  @Override
  public void update(JsonObject context) {
    this.context = context;
    if (updateHandler != null) {
      updateHandler.handle(this);
    }
  }

  @Override
  public void updateHandler(Handler<WorkerContext<JsonObject>> handler) {
    updateHandler = handler;
  }

  @Override
  public String getAddress() {
    return context.getString("address");
  }

  @Override
  public WorkerContext<JsonObject> setAddress(String address) {
    context.putString("address", address);
    return this;
  }

  @Override
  public SeedContext<JsonObject> getContext() {
    return parent;
  }

  @Override
  public WorkerContext<JsonObject> setContext(SeedContext<JsonObject> context) {
    parent = context;
    return this;
  }

}