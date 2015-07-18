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

package com.facebook.buck.parser;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.TargetNode;
import com.facebook.buck.util.immutables.BuckStyleImmutable;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;

import org.immutables.value.Value;

/**
 * Matches a {@link TargetNode} name by a specific {@link BuildTarget}.
 */
@Value.Immutable(builder = false)
@BuckStyleImmutable
abstract class AbstractBuildTargetSpec implements TargetNodeSpec {

  public static final Function<BuildTarget, BuildTargetSpec> TO_BUILD_TARGET_SPEC =
      new Function<BuildTarget, BuildTargetSpec>() {
        @Override
        public BuildTargetSpec apply(BuildTarget target) {
          return BuildTargetSpec.from(target);
        }
      };

  @Value.Parameter
  public abstract BuildTarget getBuildTarget();

  @Override
  @Value.Parameter
  public abstract BuildFileSpec getBuildFileSpec();

  public static BuildTargetSpec from(BuildTarget target) {
    return BuildTargetSpec.of(target, BuildFileSpec.fromBuildTarget(target));
  }

  @Override
  public ImmutableSet<BuildTarget> filter(Iterable<TargetNode<?>> nodes) {
    return ImmutableSet.of(getBuildTarget());
  }

  @Override
  public String toString() {
    return getBuildTarget().getFullyQualifiedName();
  }

}
