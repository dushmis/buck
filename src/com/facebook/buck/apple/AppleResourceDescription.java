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

package com.facebook.buck.apple;

import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.BuildRuleType;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.NoopBuildRule;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.infer.annotation.SuppressFieldNotInitialized;
import com.google.common.base.Optional;

import java.util.Set;

/**
 * Description for an apple_resource rule which copies resource files to the built bundle.
 */
public class AppleResourceDescription implements Description<AppleResourceDescription.Arg> {

  public static final BuildRuleType TYPE = BuildRuleType.of("apple_resource");

  @Override
  public BuildRuleType getBuildRuleType() {
    return TYPE;
  }

  @Override
  public Arg createUnpopulatedConstructorArg() {
    return new Arg();
  }

  @Override
  public <A extends Arg> BuildRule createBuildRule(
      TargetGraph targetGraph,
      BuildRuleParams params,
      BuildRuleResolver resolver,
      A args) {
    return new NoopBuildRule(params, new SourcePathResolver(resolver));
  }

  @SuppressFieldNotInitialized
  public static class Arg {
    public Set<SourcePath> dirs;
    public Set<SourcePath> files;
    public Optional<Set<SourcePath>> variants;
  }
}
