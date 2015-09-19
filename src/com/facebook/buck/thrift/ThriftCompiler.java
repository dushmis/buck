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

package com.facebook.buck.thrift;

import com.facebook.buck.rules.AbstractBuildRule;
import com.facebook.buck.rules.AddToRuleKey;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildableContext;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.Tool;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.MakeCleanDirectoryStep;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;

import java.nio.file.Path;
import java.util.Map;

import javax.annotation.Nullable;

public class ThriftCompiler extends AbstractBuildRule {

  @AddToRuleKey
  private final Tool compiler;
  @AddToRuleKey
  private final ImmutableList<String> flags;
  @AddToRuleKey(stringify = true)
  private final Path outputDir;
  @AddToRuleKey
  private final SourcePath input;
  @AddToRuleKey
  private final String language;
  @AddToRuleKey
  private final ImmutableSet<String> options;
  private final ImmutableList<Path> includeRoots;
  private final ImmutableSet<Path> headerMaps;
  @SuppressWarnings("PMD.UnusedPrivateField")
  @AddToRuleKey
  private final ImmutableMap<String, SourcePath> includes;
  @AddToRuleKey
  private final ImmutableSortedSet<String> generatedSources;

  public ThriftCompiler(
      BuildRuleParams params,
      SourcePathResolver resolver,
      Tool compiler,
      ImmutableList<String> flags,
      Path outputDir,
      SourcePath input,
      String language,
      ImmutableSet<String> options,
      ImmutableList<Path> includeRoots,
      ImmutableSet<Path> headerMaps,
      ImmutableMap<Path, SourcePath> includes,
      ImmutableSortedSet<String> generatedSources) {
    super(params, resolver);
    this.compiler = compiler;
    this.flags = flags;
    this.outputDir = outputDir;
    this.input = input;
    this.language = language;
    this.options = options;
    this.includeRoots = includeRoots;
    this.headerMaps = headerMaps;
    this.generatedSources = generatedSources;

    // Hash the layout of each potentially included thrift file dependency and it's contents.
    // We do this here, rather than returning them from `getInputsToCompareToOutput` so that
    // we can match the contents hash up with where it was laid out in the include search path.
    ImmutableMap.Builder<String, SourcePath> builder = ImmutableMap.builder();
    for (Map.Entry<Path, SourcePath> entry : includes.entrySet()) {
      builder.put(entry.getKey().toString(), entry.getValue());
    }
    this.includes = builder.build();
  }

  public static String resolveLanguageDir(String language, String source) {
    return String.format("gen-%s/%s", language, source);
  }

  @Override
  public ImmutableList<Step> getBuildSteps(
      BuildContext context, BuildableContext buildableContext) {

    for (String source : generatedSources) {
      buildableContext.recordArtifact(outputDir.resolve(resolveLanguageDir(language, source)));
    }

    return ImmutableList.of(
        new MakeCleanDirectoryStep(getProjectFilesystem(), outputDir),
        new ThriftCompilerStep(
            getProjectFilesystem().getRootPath(),
            ImmutableList.<String>builder()
                .addAll(compiler.getCommandPrefix(getResolver()))
                .addAll(flags)
                .build(),
            outputDir,
            getResolver().getPath(input),
            language,
            options,
            FluentIterable.from(headerMaps)
                .append(includeRoots)
                .toList()));
  }

  @Nullable
  @Override
  public Path getPathToOutput() {
    return null;
  }

}
