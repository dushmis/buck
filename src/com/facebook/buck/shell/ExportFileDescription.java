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

package com.facebook.buck.shell;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.BuildRuleType;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.ImplicitInputsInferringDescription;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.infer.annotation.SuppressFieldNotInitialized;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import java.nio.file.Path;
import java.nio.file.Paths;

public class ExportFileDescription implements
    Description<ExportFileDescription.Arg>,
    ImplicitInputsInferringDescription<ExportFileDescription.Arg> {

  public static final BuildRuleType TYPE = BuildRuleType.of("export_file");

  @Override
  public BuildRuleType getBuildRuleType() {
    return TYPE;
  }

  @Override
  public Arg createUnpopulatedConstructorArg() {
    return new Arg();
  }

  @Override
  public <A extends Arg> ExportFile createBuildRule(
      TargetGraph targetGraph,
      BuildRuleParams params,
      BuildRuleResolver resolver,
      A args) {
    return new ExportFile(params, new SourcePathResolver(resolver), args);
  }

  /**
   * If the src field is absent, add the name field to the list of inputs.
   */
  @Override
  public Iterable<Path> inferInputsFromConstructorArgs(
      BuildTarget buildTarget,
      ExportFileDescription.Arg constructorArg) {
    ImmutableList.Builder<Path> inputs = ImmutableList.builder();
    if (!constructorArg.src.isPresent()) {
      String name = buildTarget.getBasePathWithSlash() + buildTarget.getShortNameAndFlavorPostfix();
      inputs.add(Paths.get(name));
    }
    return inputs.build();
  }

  @SuppressFieldNotInitialized
  public static class Arg {
    public Optional<SourcePath> src;
    public Optional<String> out;
  }
}
