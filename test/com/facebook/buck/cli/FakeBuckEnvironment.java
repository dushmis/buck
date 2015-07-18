/*
 * Copyright 2013-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.cli;

import com.google.common.collect.ImmutableMap;

public class FakeBuckEnvironment extends FakeBuckConfig {

  private final ImmutableMap<String, String> environment;

  public FakeBuckEnvironment(
      ImmutableMap<String, ImmutableMap<String, String>> sections,
      ImmutableMap<String, String> environment) {
    super(sections);
    this.environment = environment;
  }

  @Override
  public ImmutableMap<String, String> getEnvironment() {
    return environment;
  }

  @Override
  public String[] getEnv(String propertyName, String separator) {
    return environment.get(propertyName).split(separator);
  }
}
