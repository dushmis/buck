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

import com.facebook.buck.shell.ShellStep;
import com.facebook.buck.step.ExecutionContext;
import com.google.common.collect.ImmutableList;

import java.nio.file.Path;

public class LexStep extends ShellStep {

  private final ImmutableList<String> lexPrefix;
  private final ImmutableList<String> flags;
  private final Path outputSource;
  private final Path outputHeader;
  private final Path input;

  public LexStep(
      Path workingDirectory,
      ImmutableList<String> lexPrefix,
      ImmutableList<String> flags,
      Path outputSource,
      Path outputHeader,
      Path input) {
    super(workingDirectory);
    this.lexPrefix = lexPrefix;
    this.flags = flags;
    this.outputSource = outputSource;
    this.outputHeader = outputHeader;
    this.input = input;
  }

  @Override
  protected ImmutableList<String> getShellCommandInternal(ExecutionContext context) {
    return ImmutableList.<String>builder()
        .addAll(lexPrefix)
        .addAll(flags)
        .add("--outfile=" + outputSource.toString())
        .add("--header-file=" + outputHeader.toString())
        .add(input.toString())
        .build();
  }

  @Override
  public String getShortName() {
    return "lex";
  }

}
