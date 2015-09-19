/*
 * Copyright 2012-present Facebook, Inc.
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

package com.facebook.buck.rules;

import com.facebook.buck.model.HasBuildTarget;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.Step;
import com.facebook.buck.test.TestCaseSummary;
import com.facebook.buck.test.TestResults;
import com.facebook.buck.test.TestResultSummary;
import com.facebook.buck.test.TestRunningOptions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.List;

/**
 * A {@link BuildRule} that is designed to run tests.
 */
public interface TestRule extends HasBuildTarget {

  /**
   * Callbacks to invoke during the test run to report information
   * about test cases and/or tests.
   */
  interface TestReportingCallback {
    void testsDidBegin();
    void testDidBegin(String testCaseName, String testName);
    void testDidEnd(TestResultSummary testResultSummary);
    void testsDidEnd(List<TestCaseSummary> testCaseSummaries);
  }

  /**
   * Implementation of {@link TestReportingCallback} which does nothing.
   */
  TestReportingCallback NOOP_REPORTING_CALLBACK = new TestReportingCallback() {
    @Override
    public void testsDidBegin() { }

    @Override
    public void testDidBegin(String testCaseName, String testName) { }

    @Override
    public void testDidEnd(TestResultSummary testResultSummary) { }

    @Override
    public void testsDidEnd(List<TestCaseSummary> testCaseSummaries) { }
  };

  /**
   * Returns a boolean indicating whether the files that contain the test results for this rule are
   * present.
   * <p>
   * If this method returns {@code true}, then
   * {@link #interpretTestResults(ExecutionContext, boolean, boolean)}
   * should be able to be called directly.
   */
  boolean hasTestResultFiles(ExecutionContext executionContext);

  /**
   * Returns the commands required to run the tests.
   * <p>
   * <strong>Note:</strong> This method may be run without
   * {@link BuildEngine#build(BuildContext, BuildRule)} having been run. This happens if the user
   * has built [and ran] the test previously and then re-runs it using the {@code --debug} flag.
   *
   * @param buildContext Because this method may be run without
   *     {@link BuildEngine#build(BuildContext, BuildRule)} having been run, this is supplied in
   *     case any non-cacheable build work needs to be done.
   * @param options The runtime testing options.
   * @param executionContext Provides context for creating {@link Step}s.
   * @return the commands required to run the tests
   */
  ImmutableList<Step> runTests(
      BuildContext buildContext,
      ExecutionContext executionContext,
      TestRunningOptions options,
      TestReportingCallback testReportingCallback);

  Callable<TestResults> interpretTestResults(
      ExecutionContext executionContext,
      boolean isUsingTestSelectors,
      boolean isDryRun);

  /**
   * @return The set of labels for this build rule.
   */
  ImmutableSet<Label> getLabels();

  /**
   * @return The set of email addresses to act as contact points for this test.
   */
  ImmutableSet<String> getContacts();

  /**
   * @return The set of {@link BuildRule} instances that this test is testing.
   */
  ImmutableSet<BuildRule> getSourceUnderTest();

  /**
   * @return The relative path to the output directory of the test rule.
   */
  Path getPathToTestOutputDirectory();

  /**
   * @return true if the test should run by itself when no other tests are run,
   * false otherwise.
   */
  boolean runTestSeparately();

  /**
   * @return true if calling {@code runTests()} on this rule invokes
   * the callbacks in {@code testReportingCallback} as the tests run,
   * false otherwise.
   */
  boolean supportsStreamingTests();
}
