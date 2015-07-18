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

package com.facebook.buck.apple;

import com.facebook.buck.shell.ShellStep;
import com.facebook.buck.step.ExecutionContext;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import java.nio.file.Path;
import java.util.List;
import java.util.SortedSet;

public class ActoolStep extends ShellStep {

  public enum BundlingMode {
    MERGE_BUNDLES,
    SEPARATE_BUNDLES,
  }

  private final String applePlatformName;
  private final ImmutableList<String> actoolCommand;
  private final SortedSet<Path> assetCatalogDirs;
  private final Path output;
  private final BundlingMode bundlingMode;

  public ActoolStep(
      String applePlatformName,
      List<String> actoolCommand,
      SortedSet<Path> assetCatalogDirs,
      Path output,
      BundlingMode bundlingMode) {
    this.applePlatformName = applePlatformName;
    this.actoolCommand = ImmutableList.copyOf(actoolCommand);
    this.assetCatalogDirs = assetCatalogDirs;
    this.output = output;
    this.bundlingMode = bundlingMode;
  }

  @Override
  protected ImmutableList<String> getShellCommandInternal(ExecutionContext context) {
    ImmutableList.Builder<String> commandBuilder = ImmutableList.builder();

    //TODO(jakubzika): Let apps select their minimum target.
    String target = "7.0";
    if (bundlingMode == BundlingMode.SEPARATE_BUNDLES) {
      // When splitting into bundles, Assets.car cannot be used, so force the deployment target to
      // a version that does not support it.
      //TODO(jakubzika): Don't do this if the apps selected target is lower than 6.0.
      target = "6.0";
    }

    commandBuilder.addAll(actoolCommand);
    commandBuilder.add(
        "--output-format", "human-readable-text",
        "--notices",
        "--warnings",
        "--errors",
        "--platform", applePlatformName,
        "--minimum-deployment-target", target,
        //TODO(jakubzika): Let apps decide which device they want to target (iPhone / iPad / both)
        "--target-device", "iphone",
        "--compress-pngs",
        "--compile",
        output.toString());

    commandBuilder.addAll(Iterables.transform(assetCatalogDirs, Functions.toStringFunction()));

    return commandBuilder.build();
  }

  @Override
  public String getShortName() {
    return "actool";
  }
}
