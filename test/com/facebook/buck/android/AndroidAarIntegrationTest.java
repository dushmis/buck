/*
 * Copyright 2015-present Facebook, Inc.
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

package com.facebook.buck.android;

import static org.junit.Assert.assertThat;

import com.facebook.buck.testutil.integration.DebuggableTemporaryFolder;
import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.TestDataHelper;
import com.facebook.buck.testutil.integration.ZipInspector;
import com.facebook.buck.zip.Unzip;

import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.jar.JarFile;

public class AndroidAarIntegrationTest {

  @Rule
  public DebuggableTemporaryFolder tmp = new DebuggableTemporaryFolder();

  @Before
  public void setUp() throws IOException {
    AssumeAndroidPlatform.assumeSdkIsAvailable();
  }

  @Test
  public void testBuildAndroidAar() throws IOException {
    ProjectWorkspace workspace = TestDataHelper.createProjectWorkspaceForScenario(
        this,
        "android_aar_build/caseA",
        tmp);
    workspace.setUp();
    workspace.runBuckBuild("//:app").assertSuccess();

    Path aar = workspace.getPath("buck-out/gen/app.aar");
    ZipInspector zipInspector = new ZipInspector(aar);
    zipInspector.assertFileExists("AndroidManifest.xml");
    zipInspector.assertFileExists("classes.jar");
    zipInspector.assertFileExists("R.txt");
    zipInspector.assertFileExists("assets/a.txt");
    zipInspector.assertFileExists("assets/b.txt");
    zipInspector.assertFileExists("res/raw/helloworld.txt");
    zipInspector.assertFileExists("res/values/values.xml");
    zipInspector.assertFileContents(
        "res/values/values.xml",
        workspace.getFileContents("res/values/A.xml").trim()
    );

    Path contents = tmp.getRootPath().resolve("aar-contents");
    Unzip.extractZipFile(aar, contents, Unzip.ExistingFileMode.OVERWRITE);
    try (JarFile classes = new JarFile(contents.resolve("classes.jar").toFile())) {
      assertThat(classes.getJarEntry("com/example/HelloWorld.class"), Matchers.notNullValue());
    }

  }

  @Test
  public void testBuildPrebuiltAndroidAar() throws IOException {
    ProjectWorkspace workspace = TestDataHelper.createProjectWorkspaceForScenario(
        this,
        "android_aar_build/caseB",
        tmp);
    workspace.setUp();
    workspace.runBuckBuild("//:app").assertSuccess();

    ZipInspector zipInspector = new ZipInspector(workspace.getPath("buck-out/gen/app.aar"));
    zipInspector.assertFileExists("AndroidManifest.xml");
    zipInspector.assertFileExists("classes.jar");
    zipInspector.assertFileExists("R.txt");
    zipInspector.assertFileExists("res/");
    zipInspector.assertFileExists("res/values/");
    zipInspector.assertFileExists("res/values/values.xml");

    zipInspector.assertFileContents(
        "res/values/values.xml",
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
        "<resources>\n" +
        "    <string name=\"app_name\">Hello World</string>\n" +
        "</resources>");
  }

  @Test
  public void testCxxLibraryDependent() throws IOException {
    AssumeAndroidPlatform.assumeNdkIsAvailable();
    ProjectWorkspace workspace = TestDataHelper.createProjectWorkspaceForScenario(
        this,
        "android_aar_native_deps/cxx_deps",
        tmp);
    workspace.setUp();
    workspace.runBuckBuild("//:app").assertSuccess();

    ZipInspector zipInspector = new ZipInspector(workspace.getPath("buck-out/gen/app.aar"));
    zipInspector.assertFileExists("AndroidManifest.xml");
    zipInspector.assertFileExists("classes.jar");
    zipInspector.assertFileExists("R.txt");
    zipInspector.assertFileExists("res/");
    zipInspector.assertFileExists("jni/armeabi/libdep.so");
    zipInspector.assertFileExists("jni/armeabi/libnative.so");
    zipInspector.assertFileExists("jni/armeabi-v7a/libdep.so");
    zipInspector.assertFileExists("jni/armeabi-v7a/libnative.so");
    zipInspector.assertFileExists("jni/x86/libdep.so");
    zipInspector.assertFileExists("jni/x86/libnative.so");
  }

  @Test
  public void testNativeLibraryDependent() throws IOException {
    AssumeAndroidPlatform.assumeNdkIsAvailable();
    ProjectWorkspace workspace = TestDataHelper.createProjectWorkspaceForScenario(
        this,
        "android_aar_native_deps/ndk_deps",
        tmp);
    workspace.setUp();
    workspace.runBuckBuild("//:app").assertSuccess();

    ZipInspector zipInspector = new ZipInspector(workspace.getPath("buck-out/gen/app.aar"));
    zipInspector.assertFileExists("AndroidManifest.xml");
    zipInspector.assertFileExists("classes.jar");
    zipInspector.assertFileExists("R.txt");
    zipInspector.assertFileExists("res/");
    zipInspector.assertFileExists("assets/lib/armeabi/libfoo.so");
    zipInspector.assertFileExists("assets/lib/armeabi-v7a/libfoo.so");
    zipInspector.assertFileExists("assets/lib/x86/libfoo.so");
    zipInspector.assertFileExists("jni/armeabi/libbar.so");
    zipInspector.assertFileExists("jni/armeabi-v7a/libbar.so");
    zipInspector.assertFileExists("jni/x86/libbar.so");
  }

  @Test
  public void testEmptyExceptManifest() throws IOException {
    AssumeAndroidPlatform.assumeNdkIsAvailable();
    ProjectWorkspace workspace = TestDataHelper.createProjectWorkspaceForScenario(
        this,
        "android_project",
        tmp);
    workspace.setUp();
    workspace.runBuckBuild("//apps/sample:nearly_empty_aar").assertSuccess();
  }
}
