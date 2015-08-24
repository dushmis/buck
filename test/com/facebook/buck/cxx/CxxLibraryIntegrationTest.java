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

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeThat;
import static org.junit.Assume.assumeTrue;

import com.facebook.buck.testutil.integration.DebuggableTemporaryFolder;
import com.facebook.buck.testutil.integration.InferHelper;
import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.TestDataHelper;
import com.facebook.buck.util.environment.Platform;

import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;

public class CxxLibraryIntegrationTest {

  @Rule
  public DebuggableTemporaryFolder tmp = new DebuggableTemporaryFolder();

  @Test
  public void exportedPreprocessorFlagsApplyToBothTargetAndDependents() throws IOException {
    ProjectWorkspace workspace = TestDataHelper.createProjectWorkspaceForScenario(
        this, "exported_preprocessor_flags", tmp);
    workspace.setUp();
    workspace.runBuckBuild("//:main").assertSuccess();
  }

  @Test
  public void appleBinaryBuildsOnApplePlatform() throws IOException {
    assumeThat(Platform.detect(), is(Platform.MACOS));

    ProjectWorkspace workspace = TestDataHelper.createProjectWorkspaceForScenario(
        this, "apple_cxx_library", tmp);
    workspace.setUp();
    workspace.runBuckBuild("//:main#iphonesimulator-i386").assertSuccess();
  }

  @Test
  public void appleLibraryBuildsOnApplePlatform() throws IOException {
    assumeThat(Platform.detect(), is(Platform.MACOS));

    ProjectWorkspace workspace = TestDataHelper.createProjectWorkspaceForScenario(
        this, "apple_cxx_library", tmp);
    workspace.setUp();
    workspace.runBuckBuild("//:lib#iphonesimulator-i386,static").assertSuccess();
  }

  @Test
  public void libraryCanIncludeAllItsHeadersAndExportedHeadersOfItsDeps() throws IOException {
    ProjectWorkspace workspace = TestDataHelper.createProjectWorkspaceForScenario(
        this, "private_and_exported_headers", tmp);
    workspace.setUp();

    ProjectWorkspace.ProcessResult result = workspace.runBuckBuild("//:good-bin");
    result.assertSuccess();
  }

  @Test
  public void libraryCannotIncludePrivateHeadersOfDeps() throws IOException {
    ProjectWorkspace workspace = TestDataHelper.createProjectWorkspaceForScenario(
        this, "private_and_exported_headers", tmp);
    workspace.setUp();

    ProjectWorkspace.ProcessResult result = workspace.runBuckBuild("//:bad-bin");
    result.assertFailure();
  }

  @Test
  public void libraryBuildPathIsSoName() throws IOException {
    assumeTrue(Platform.detect() == Platform.LINUX);
    ProjectWorkspace workspace = TestDataHelper.createProjectWorkspaceForScenario(
        this, "shared_library", tmp);
    workspace.setUp();
    ProjectWorkspace.ProcessResult result = workspace.runBuckBuild("//:binary");
    assertTrue(
        Files.isRegularFile(
            workspace.getPath(
                "buck-out/gen/subdir/" +
                    "library#default,shared/libsubdir_library.so")));
    result.assertSuccess();
  }

  @Test
  public void forceStaticLibLinkedIntoSharedContextIsBuiltWithPic() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "force_static_pic", tmp);
    workspace.setUp();
    workspace.runBuckBuild("//:foo#shared,default").assertSuccess();
  }

  @Test
  public void runInferOnSimpleLibraryWithoutDeps() throws IOException {
    assumeTrue(Platform.detect() != Platform.WINDOWS);
    ProjectWorkspace workspace = InferHelper.setupCxxInferWorkspace(this, tmp);
    workspace.runBuckBuild("//foo:dep_one").assertSuccess();
  }

}
