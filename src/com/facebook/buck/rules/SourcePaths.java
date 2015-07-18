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

package com.facebook.buck.rules;

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;

import java.nio.file.Path;

/**
 * Utilities for dealing with {@link SourcePath}s.
 */
public class SourcePaths {

  public static final Function<BuildTargetSourcePath, BuildTarget> TO_BUILD_TARGET =
      new Function<BuildTargetSourcePath, BuildTarget>() {
        @Override
        public BuildTarget apply(BuildTargetSourcePath input) {
          return input.getTarget();
        }
      };

  /** Utility class: do not instantiate. */
  private SourcePaths() {}

  public static Iterable<BuildTarget> filterBuildTargetSourcePaths(
      Iterable<SourcePath> sourcePaths) {
    return FluentIterable
        .from(sourcePaths)
        .filter(BuildTargetSourcePath.class)
        .transform(TO_BUILD_TARGET);
  }

  public static Function<Path, SourcePath> toSourcePath(final ProjectFilesystem projectFilesystem) {
    return new Function<Path, SourcePath>() {
      @Override
      public SourcePath apply(Path input) {
        return new PathSourcePath(projectFilesystem, input);
      }
    };
  }

  public static Function<BuildRule, SourcePath> getToBuildTargetSourcePath() {
    return new Function<BuildRule, SourcePath>() {
      @Override
      public SourcePath apply(BuildRule input) {
        return new BuildTargetSourcePath(input.getBuildTarget());
      }
    };
  }

  public static Function<Path, SourcePath> getToBuildTargetSourcePath(
      final BuildTarget target) {
    return new Function<Path, SourcePath>() {
      @Override
      public SourcePath apply(Path input) {
        return new BuildTargetSourcePath(target, input);
      }
    };
  }

}
