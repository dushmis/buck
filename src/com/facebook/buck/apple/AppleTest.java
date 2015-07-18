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

package com.facebook.buck.apple;

import com.facebook.buck.rules.Tool;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.rules.AddToRuleKey;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.HasRuntimeDeps;
import com.facebook.buck.rules.Label;
import com.facebook.buck.rules.NoopBuildRule;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.TestRule;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.MakeCleanDirectoryStep;
import com.facebook.buck.test.TestCaseSummary;
import com.facebook.buck.test.TestResults;
import com.facebook.buck.test.selectors.TestSelectorList;
import com.facebook.buck.util.BuckConstant;
import com.facebook.buck.util.HumanReadableException;
import com.facebook.buck.zip.UnzipStep;
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@SuppressWarnings("PMD.TestClassWithoutTestCases")
public class AppleTest extends NoopBuildRule implements TestRule, HasRuntimeDeps {

  @AddToRuleKey
  private final Optional<Path> xctoolPath;

  @AddToRuleKey
  private final Optional<BuildRule> xctoolZipRule;

  @AddToRuleKey
  private final Tool xctest;

  @AddToRuleKey
  private final Optional<Tool> otest;

  @AddToRuleKey
  private final boolean useXctest;

  @AddToRuleKey
  private final String platformName;

  @AddToRuleKey
  private final Optional<String> simulatorName;

  @AddToRuleKey
  private final BuildRule testBundle;

  @AddToRuleKey
  private final Optional<AppleBundle> testHostApp;

  private final ImmutableSet<String> contacts;
  private final ImmutableSet<Label> labels;

  private final Path xctoolUnzipDirectory;
  private final Path testOutputPath;

  private final String testBundleExtension;

  private Optional<AppleTestXctoolStdoutReader> xctoolStdoutReader;

  private static class AppleTestXctoolStdoutReader
    implements XctoolRunTestsStep.StdoutReadingCallback {

    private final TestCaseSummariesBuildingXctoolEventHandler xctoolEventHandler;

    public AppleTestXctoolStdoutReader(TestRule.TestReportingCallback testReportingCallback) {
      this.xctoolEventHandler = new TestCaseSummariesBuildingXctoolEventHandler(
          testReportingCallback);
    }

    @Override
    public void readStdout(InputStream stdout) throws IOException {
      try (InputStreamReader stdoutReader =
               new InputStreamReader(stdout, StandardCharsets.UTF_8);
           BufferedReader bufferedReader = new BufferedReader(stdoutReader)) {
        XctoolOutputParsing.streamOutputFromReader(bufferedReader, xctoolEventHandler);
      }
    }

    public ImmutableList<TestCaseSummary> getTestCaseSummaries() {
      return xctoolEventHandler.getTestCaseSummaries();
    }
  }

  AppleTest(
      Optional<Path> xctoolPath,
      Optional<BuildRule> xctoolZipRule,
      Tool xctest,
      Optional<Tool> otest,
      Boolean useXctest,
      String platformName,
      Optional<String> simulatorName,
      BuildRuleParams params,
      SourcePathResolver resolver,
      BuildRule testBundle,
      Optional<AppleBundle> testHostApp,
      String testBundleExtension,
      ImmutableSet<String> contacts,
      ImmutableSet<Label> labels) {
    super(params, resolver);
    this.xctoolPath = xctoolPath;
    this.xctoolZipRule = xctoolZipRule;
    this.useXctest = useXctest;
    this.xctest = xctest;
    this.otest = otest;
    this.platformName = platformName;
    this.simulatorName = simulatorName;
    this.testBundle = testBundle;
    this.testHostApp = testHostApp;
    this.contacts = contacts;
    this.labels = labels;
    this.testBundleExtension = testBundleExtension;
    this.xctoolUnzipDirectory = BuildTargets.getScratchPath(
        params.getBuildTarget(),
        "__xctool_%s__");
    this.testOutputPath = getPathToTestOutputDirectory().resolve("test-output.json");
    this.xctoolStdoutReader = Optional.absent();
  }

  /**
   * Returns the test bundle to run.
   */
  public BuildRule getTestBundle() {
    return testBundle;
  }

  @Override
  public ImmutableSet<Label> getLabels() {
    return labels;
  }

  @Override
  public ImmutableSet<String> getContacts() {
    return contacts;
  }

  @Override
  public ImmutableSet<BuildRule> getSourceUnderTest() {
    // Apple tests always express a rule -> test dependency, not the other way
    // around.
    return ImmutableSet.of();
  }

  @Override
  public boolean hasTestResultFiles(ExecutionContext executionContext) {
    return executionContext.getProjectFilesystem().exists(testOutputPath);
  }

  @Override
  public ImmutableList<Step> runTests(
      BuildContext buildContext,
      ExecutionContext executionContext,
      boolean isDryRun,
      boolean isShufflingTests,
      TestSelectorList testSelectorList,
      TestRule.TestReportingCallback testReportingCallback) {
    ImmutableList.Builder<Step> steps = ImmutableList.builder();
    Path resolvedTestBundleDirectory = executionContext.getProjectFilesystem().resolve(
        Preconditions.checkNotNull(testBundle.getPathToOutput()));

    Path pathToTestOutput = executionContext.getProjectFilesystem().resolve(
        getPathToTestOutputDirectory());
    steps.add(new MakeCleanDirectoryStep(pathToTestOutput));

    Path resolvedTestOutputPath = executionContext.getProjectFilesystem().resolve(
        testOutputPath);

    Optional<Path> testHostAppPath = Optional.absent();
    if (testHostApp.isPresent()) {
      Path resolvedTestHostAppDirectory = executionContext.getProjectFilesystem().resolve(
          Preconditions.checkNotNull(testHostApp.get().getPathToOutput()));
      testHostAppPath = Optional.of(
          resolvedTestHostAppDirectory.resolve(
              testHostApp.get().getUnzippedOutputFilePathToBinary()));
    }

    if (!useXctest) {
      if (!xctoolPath.isPresent() && !xctoolZipRule.isPresent()) {
        throw new HumanReadableException(
            "Set xctool_path = /path/to/xctool or xctool_zip_target = //path/to:xctool-zip " +
            "in the [apple] section of .buckconfig to run this test");
      }

      ImmutableSet.Builder<Path> logicTestPathsBuilder = ImmutableSet.builder();
      ImmutableMap.Builder<Path, Path> appTestPathsToHostAppsBuilder = ImmutableMap.builder();

      if (testHostAppPath.isPresent()) {
        appTestPathsToHostAppsBuilder.put(
            resolvedTestBundleDirectory,
            testHostAppPath.get());
      } else {
        logicTestPathsBuilder.add(resolvedTestBundleDirectory);
      }

      Path xctoolBinaryPath;

      if (xctoolZipRule.isPresent()) {
        Path resolvedXctoolUnzipDirectory = executionContext.getProjectFilesystem().resolve(
            xctoolUnzipDirectory);
        steps.add(new MakeCleanDirectoryStep(resolvedXctoolUnzipDirectory));
        steps.add(
            new UnzipStep(
                // This is added as a runtime dependency via getRuntimeDeps() earlier.
                Preconditions.checkNotNull(xctoolZipRule.get().getPathToOutput()),
                resolvedXctoolUnzipDirectory));
        xctoolBinaryPath = resolvedXctoolUnzipDirectory.resolve("bin/xctool");
      } else {
        xctoolBinaryPath = xctoolPath.get();
      }

      xctoolStdoutReader = Optional.of(new AppleTestXctoolStdoutReader(testReportingCallback));
      steps.add(
          new XctoolRunTestsStep(
              xctoolBinaryPath,
              platformName,
              simulatorName,
              logicTestPathsBuilder.build(),
              appTestPathsToHostAppsBuilder.build(),
              resolvedTestOutputPath,
              xctoolStdoutReader));
    } else {
      Tool testRunningTool;
      if (testBundleExtension == "xctest") {
        testRunningTool = xctest;
      } else if (otest.isPresent()) {
        testRunningTool = otest.get();
      } else {
        throw new HumanReadableException(
            "Cannot run non-xctest bundle type %s (otest not present)",
            testBundleExtension);
      }
      steps.add(
          new XctestRunTestsStep(
              testRunningTool.getCommandPrefix(getResolver()),
              (testBundleExtension == "xctest" ? "-XCTest" : "-SenTest"),
              resolvedTestBundleDirectory,
              resolvedTestOutputPath));
    }

    return steps.build();
  }

  @Override
  public Callable<TestResults> interpretTestResults(
      final ExecutionContext executionContext,
      boolean isUsingTestSelectors,
      boolean isDryRun) {
    return new Callable<TestResults>() {
      @Override
      public TestResults call() throws Exception {
        List<TestCaseSummary> testCaseSummaries;
        if (xctoolStdoutReader.isPresent()) {
          // We've already run the tests with 'xctool' and parsed
          // their output; no need to parse the same output again.
          testCaseSummaries = xctoolStdoutReader.get().getTestCaseSummaries();
        } else {
          Path resolvedOutputPath = executionContext.getProjectFilesystem().resolve(testOutputPath);
          try (BufferedReader reader =
              Files.newBufferedReader(resolvedOutputPath, StandardCharsets.UTF_8)) {
            if (useXctest) {
              testCaseSummaries = XctestOutputParsing.parseOutputFromReader(reader);
            } else {
              TestCaseSummariesBuildingXctoolEventHandler xctoolEventHandler =
                  new TestCaseSummariesBuildingXctoolEventHandler(NOOP_REPORTING_CALLBACK);
              XctoolOutputParsing.streamOutputFromReader(reader, xctoolEventHandler);
              testCaseSummaries = xctoolEventHandler.getTestCaseSummaries();
            }
          }
        }
        return new TestResults(
          getBuildTarget(),
          testCaseSummaries,
          contacts,
          FluentIterable.from(labels).transform(Functions.toStringFunction()).toSet());
      }
    };
  }

  @Override
  public Path getPathToTestOutputDirectory() {
    // TODO(user): Refactor the JavaTest implementation; this is identical.

    List<String> pathsList = new ArrayList<>();
    pathsList.add(getBuildTarget().getBaseNameWithSlash());
    pathsList.add(
        String.format("__apple_test_%s_output__", getBuildTarget().getShortNameAndFlavorPostfix()));

    // Putting the one-time test-sub-directory below the usual directory has the nice property that
    // doing a test run without "--one-time-output" will tidy up all the old one-time directories!
    String subdir = BuckConstant.oneTimeTestSubdirectory;
    if (subdir != null && !subdir.isEmpty()) {
      pathsList.add(subdir);
    }

    String[] pathsArray = pathsList.toArray(new String[pathsList.size()]);
    return Paths.get(BuckConstant.GEN_DIR, pathsArray);
  }

  @Override
  public boolean runTestSeparately() {
    // Tests which run in the simulator must run separately from all other tests;
    // there's a 20 second timeout hard-coded in the iOS Simulator SpringBoard which
    // is hit any time the host is overloaded.
    return testHostApp.isPresent();
  }

  // This test rule just executes the test bundle, so we need it available locally.
  @Override
  public ImmutableSortedSet<BuildRule> getRuntimeDeps() {
    ImmutableSortedSet.Builder<BuildRule> runtimeDepsBuilder =
        ImmutableSortedSet.<BuildRule>naturalOrder()
            .add(testBundle)
            .addAll(testHostApp.asSet());

    if (xctoolZipRule.isPresent()) {
      runtimeDepsBuilder.add(xctoolZipRule.get());
    }

    return runtimeDepsBuilder.build();
  }

  @Override
  public boolean supportsStreamingTests() {
    return !useXctest;
  }
}
