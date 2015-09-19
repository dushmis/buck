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

import com.facebook.buck.shell.ShellStep;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.util.MoreIterables;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import java.nio.file.Path;

public class ThriftCompilerStep extends ShellStep {

  private final ImmutableList<String> compilerPrefix;
  private final Path outputDir;
  private final Path input;
  private final String language;
  private final ImmutableSet<String> options;
  private final ImmutableList<Path> includes;

  public ThriftCompilerStep(
      Path workingDirectory,
      ImmutableList<String> compilerPrefix,
      Path outputDir,
      Path input,
      String language,
      ImmutableSet<String> options,
      ImmutableList<Path> includes) {
    super(workingDirectory);
    this.compilerPrefix = compilerPrefix;
    this.outputDir = outputDir;
    this.input = input;
    this.language = language;
    this.options = options;
    this.includes = includes;
  }

  @Override
  protected ImmutableList<String> getShellCommandInternal(ExecutionContext context) {
    return ImmutableList.<String>builder()
        .addAll(compilerPrefix)
        .add("--gen", String.format("%s:%s", language, Joiner.on(',').join(options)))
        .addAll(
            MoreIterables.zipAndConcat(
                Iterables.cycle("-I"),
                Iterables.transform(includes, Functions.toStringFunction())))
        .add("-o", outputDir.toString())
        .add(input.toString())
        .build();
  }

  @Override
  public String getShortName() {
    return "thrift";
  }

  @Override
  public boolean equals(Object o) {

    if (this == o) {
      return true;
    }

    if (!(o instanceof ThriftCompilerStep)) {
      return false;
    }

    ThriftCompilerStep that = (ThriftCompilerStep) o;

    if (!compilerPrefix.equals(that.compilerPrefix)) {
      return false;
    }

    if (!includes.equals(that.includes)) {
      return false;
    }

    if (!input.equals(that.input)) {
      return false;
    }

    if (!language.equals(that.language)) {
      return false;
    }

    if (!options.equals(that.options)) {
      return false;
    }

    if (!outputDir.equals(that.outputDir)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(compilerPrefix, outputDir, input, language, options, includes);
  }

}
