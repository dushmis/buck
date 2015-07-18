/*
 * Copyright 2015-present Facebook, Inc.
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

package com.facebook.buck.android;

import static com.facebook.buck.java.JavaCompilationConstants.ANDROID_JAVAC_OPTIONS;

import com.facebook.buck.cli.FakeBuckConfig;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.AbstractNodeBuilder;
import com.facebook.buck.rules.SourcePath;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.util.concurrent.MoreExecutors;

public class AndroidInstrumentationApkBuilder
    extends AbstractNodeBuilder<AndroidInstrumentationApkDescription.Arg> {

  private AndroidInstrumentationApkBuilder(BuildTarget target) {
    super(
        new AndroidInstrumentationApkDescription(
            new ProGuardConfig(new FakeBuckConfig()),
            ANDROID_JAVAC_OPTIONS,
            ImmutableMap.<NdkCxxPlatforms.TargetCpuType, NdkCxxPlatform>of(),
            MoreExecutors.newDirectExecutorService()),
        target);
  }

  public static AndroidInstrumentationApkBuilder createBuilder(BuildTarget buildTarget) {
    return new AndroidInstrumentationApkBuilder(buildTarget);
  }

  public AndroidInstrumentationApkBuilder setManifest(SourcePath manifest) {
    arg.manifest = manifest;
    return this;
  }

  public AndroidInstrumentationApkBuilder setDeps(ImmutableSortedSet<BuildTarget> deps) {
    arg.deps = Optional.of(deps);
    return this;
  }

  public AndroidInstrumentationApkBuilder setApk(BuildTarget apk) {
    arg.apk = apk;
    return this;
  }

}
