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
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.rules.AbstractBuildRule;
import com.facebook.buck.rules.AddToRuleKey;
import com.facebook.buck.rules.BinaryBuildRule;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildTargetSourcePath;
import com.facebook.buck.rules.BuildableContext;
import com.facebook.buck.rules.CommandTool;
import com.facebook.buck.rules.HasRuntimeDeps;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.Tool;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.MakeCleanDirectoryStep;
import com.facebook.buck.step.fs.MakeExecutableStep;
import com.facebook.buck.step.fs.StringTemplateStep;
import com.facebook.buck.util.Escaper;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;

import org.stringtemplate.v4.ST;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

public class ShBinary extends AbstractBuildRule implements BinaryBuildRule, HasRuntimeDeps {

  private static final Path TEMPLATE = Paths.get(
      System.getProperty(
          "buck.path_to_sh_binary_template",
          "src/com/facebook/buck/shell/sh_binary_template"));

  @AddToRuleKey
  private final SourcePath main;
  @AddToRuleKey
  private final ImmutableSet<SourcePath> resources;

  /** The path where the output will be written. */
  private final Path output;

  protected ShBinary(
      BuildRuleParams params,
      SourcePathResolver resolver,
      SourcePath main,
      ImmutableSet<SourcePath> resources) {
    super(params, resolver);
    this.main = main;
    this.resources = resources;

    BuildTarget target = params.getBuildTarget();
    this.output = BuildTargets.getGenPath(
        target,
        String.format("__%%s__/%s.sh", target.getShortNameAndFlavorPostfix()));
  }

  @Override
  public ImmutableList<Step> getBuildSteps(
      BuildContext context,
      BuildableContext buildableContext) {
    buildableContext.recordArtifact(output);

    return ImmutableList.of(
        new MakeCleanDirectoryStep(output.getParent()),
        new StringTemplateStep(
            TEMPLATE,
            output,
            new Function<ST, ST>() {
              @Override
              public ST apply(ST input) {
                // Generate an .sh file that builds up an environment and invokes the user's script.
                // This generated .sh file will be returned by getExecutableCommand().
                // This script can be cached and used on machines other than the one where it was
                // created. That means it can't contain any absolute filepaths. Expose the absolute
                // filepath of the root of the project as $BUCK_REAL_ROOT, determined at runtime.
                int levelsBelowRoot = output.getNameCount() - 1;
                String pathBackToRoot = Joiner
                    .on("/")
                    .join(Collections.nCopies(levelsBelowRoot, ".."));

                ImmutableList<String> resourceStrings = FluentIterable
                    .from(getResolver().getAllPaths(resources))
                    .transform(Functions.toStringFunction())
                    .transform(Escaper.BASH_ESCAPER)
                    .toList();

                return input
                    .add("path_back_to_root", pathBackToRoot)
                    .add("script_to_run", Escaper.escapeAsBashString(getResolver().getPath(main)))
                    .add("resources", resourceStrings);
              }
            }),
        new MakeExecutableStep(output));
  }

  @Override
  public Path getPathToOutput() {
    return output;
  }

  @Override
  public Tool getExecutableCommand() {
    return new CommandTool.Builder()
        .addArg(new BuildTargetSourcePath(getBuildTarget(), output))
        .build();
  }

  // If the script is generated from another build rule, it needs to be available on disk
  // for this rule to be usable.
  @Override
  public ImmutableSortedSet<BuildRule> getRuntimeDeps() {
    return ImmutableSortedSet.copyOf(getResolver().filterBuildRuleInputs(main));
  }

}
