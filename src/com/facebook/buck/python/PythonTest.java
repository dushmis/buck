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

package com.facebook.buck.python;

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.HasRuntimeDeps;
import com.facebook.buck.rules.Label;
import com.facebook.buck.rules.NoopBuildRule;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.TestRule;
import com.facebook.buck.shell.ShellStep;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.MakeCleanDirectoryStep;
import com.facebook.buck.test.TestCaseSummary;
import com.facebook.buck.test.TestResultSummary;
import com.facebook.buck.test.TestResults;
import com.facebook.buck.test.selectors.TestSelectorList;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@SuppressWarnings("PMD.TestClassWithoutTestCases")
public class PythonTest extends NoopBuildRule implements TestRule, HasRuntimeDeps {

  private final PythonBinary binary;
  private final ImmutableSortedSet<BuildRule> additionalDeps;
  private final ImmutableSet<Label> labels;
  private final ImmutableSet<String> contacts;
  private final ImmutableSet<BuildRule> sourceUnderTest;

  public PythonTest(
      BuildRuleParams params,
      SourcePathResolver resolver,
      PythonBinary binary,
      ImmutableSortedSet<BuildRule> additionalDeps,
      ImmutableSet<BuildRule> sourceUnderTest,
      ImmutableSet<Label> labels,
      ImmutableSet<String> contacts) {

    super(params, resolver);

    this.binary = binary;
    this.additionalDeps = additionalDeps;
    this.sourceUnderTest = sourceUnderTest;
    this.labels = labels;
    this.contacts = contacts;
  }

  private Step getRunTestStep() {
    return new ShellStep() {

      @Override
      protected ImmutableList<String> getShellCommandInternal(ExecutionContext context) {
        ProjectFilesystem fs = context.getProjectFilesystem();
        ImmutableList.Builder<String> builder = new ImmutableList.Builder<>();
        builder.addAll(binary.getExecutableCommand().getCommandPrefix(getResolver()));
        builder.add("-o", fs.resolve(getPathToTestOutputResult()).toString());
        return builder.build();
      }

      @Override
      public String getShortName() {
        return "pyunit";
      }

    };
  }

  @Override
  public ImmutableList<Step> runTests(
      BuildContext buildContext,
      ExecutionContext executionContext,
      boolean isDryRun,
      boolean isShufflingTests,
      TestSelectorList testSelectorList,
      TestRule.TestReportingCallback testReportingCallback) {
    return ImmutableList.of(
        new MakeCleanDirectoryStep(getPathToTestOutputDirectory()),
        getRunTestStep());
  }

  @Override
  public ImmutableSet<String> getContacts() {
    return contacts;
  }

  @Override
  public Path getPathToTestOutputDirectory() {
    return BuildTargets.getGenPath(
        getBuildTarget(),
        "__test_%s_output__");
  }

  public Path getPathToTestOutputResult() {
    return getPathToTestOutputDirectory().resolve("results.json");
  }

  @Override
  public boolean hasTestResultFiles(ExecutionContext executionContext) {
    ProjectFilesystem filesystem = executionContext.getProjectFilesystem();
    return filesystem.isFile(getPathToTestOutputResult());
  }

  @Override
  public ImmutableSet<BuildRule> getSourceUnderTest() {
    return sourceUnderTest;
  }

  @Override
  public ImmutableSet<Label> getLabels() {
    return labels;
  }

  @Override
  public Callable<TestResults> interpretTestResults(
      final ExecutionContext executionContext,
      boolean isUsingTestSelectors,
      final boolean isDryRun) {
    return new Callable<TestResults>() {
      @Override
      public TestResults call() throws Exception {
        ImmutableList.Builder<TestCaseSummary> summaries = ImmutableList.builder();
        if (!isDryRun) {
          Optional<String> resultsFileContents =
              executionContext.getProjectFilesystem().readFileIfItExists(
                  getPathToTestOutputResult());
          ObjectMapper mapper = executionContext.getObjectMapper();
          TestResultSummary[] testResultSummaries = mapper.readValue(
              resultsFileContents.get(),
              TestResultSummary[].class);
          summaries.add(new TestCaseSummary(
              getBuildTarget().getFullyQualifiedName(),
              ImmutableList.copyOf(testResultSummaries)));
        }
        return new TestResults(
            getBuildTarget(),
            summaries.build(),
            contacts,
            FluentIterable.from(labels).transform(Functions.toStringFunction()).toSet());
      }
    };
  }

  @Override
  public boolean runTestSeparately() {
    return false;
  }

  // A python test rule is actually just a {@link NoopBuildRule} which contains a references to
  // a {@link PythonBinary} rule, which is the actual test binary.  Therefore, we *need* this
  // rule around to run this test, so model this via the {@link HasRuntimeDeps} interface.
  @Override
  public ImmutableSortedSet<BuildRule> getRuntimeDeps() {
    return ImmutableSortedSet.<BuildRule>naturalOrder()
        .add(binary)
        .addAll(additionalDeps)
        .build();
  }

  @Override
  public boolean supportsStreamingTests() {
    return false;
  }

  @VisibleForTesting
  protected PythonBinary getBinary() {
    return binary;
  }

}
