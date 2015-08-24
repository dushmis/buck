/*
 * Copyright 2014-present Facebook, Inc.
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

package com.facebook.buck.cxx;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.FlavorDomain;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

public class CxxTestBuilder extends AbstractCxxSourceBuilder<CxxTestDescription.Arg> {

  public CxxTestBuilder(
      BuildTarget target,
      CxxBuckConfig cxxBuckConfig,
      CxxPlatform defaultCxxPlatform,
      FlavorDomain<CxxPlatform> cxxPlatforms) {
    super(new CxxTestDescription(cxxBuckConfig, defaultCxxPlatform, cxxPlatforms), target);
  }

  public CxxTestBuilder(BuildTarget target) {
    this(target, createDefaultConfig(), createDefaultPlatform(), createDefaultPlatforms());
  }

  public CxxTestBuilder setEnv(ImmutableMap<String, String> env) {
    arg.env = Optional.of(env);
    return this;
  }

  public CxxTestBuilder setRunTestSeparately(boolean runTestSeparately) {
    arg.runTestSeparately = Optional.of(runTestSeparately);
    return this;
  }

  public CxxTestBuilder setFramework(CxxTestType framework) {
    arg.framework = Optional.of(framework);
    return this;
  }

}
