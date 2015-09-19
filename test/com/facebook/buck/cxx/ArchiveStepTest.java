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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.shell.ShellStep;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.TestExecutionContext;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

public class ArchiveStepTest {

  @Test
  public void testArchiveStepUsesCorrectCommand() {
    ProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    ExecutionContext context = TestExecutionContext.newInstance();

    // Setup dummy values for the archiver, output, and inputs.
    ImmutableList<String> archiver = ImmutableList.of("ar");
    Path output = Paths.get("libfoo.a");
    ImmutableList<Path> inputs = ImmutableList.of(
        Paths.get("a.o"),
        Paths.get("b.o"),
        Paths.get("c.o"));

    // Create and archive step.
    ArchiveStep archiveStep = new ArchiveStep(
        projectFilesystem.getRootPath(),
        archiver,
        output,
        inputs);

    ImmutableList<Step> steps = ImmutableList.copyOf(archiveStep);
    assertEquals(1, steps.size());
    assertTrue(steps.get(0) instanceof ShellStep);
    ShellStep shellStep = (ShellStep) steps.get(0);

    // Verify that the shell command is correct.
    ImmutableList<String> expected = ImmutableList.<String>builder()
        .addAll(archiver)
        .add("rcs")
        .add(output.toString())
        .addAll(Iterables.transform(inputs, Functions.toStringFunction()))
        .build();
    ImmutableList<String> actual = shellStep.getShellCommand(context);
    assertEquals(expected, actual);
  }

}
