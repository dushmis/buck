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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.facebook.buck.cli.FakeBuckConfig;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.BuildTargetSourcePath;
import com.facebook.buck.rules.FakeBuildRule;
import com.facebook.buck.rules.FakeBuildRuleParamsBuilder;
import com.facebook.buck.rules.PathSourcePath;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.TestSourcePath;
import com.facebook.buck.testutil.AllExistingProjectFilesystem;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.util.HumanReadableException;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ThriftBuckConfigTest {

  private static FakeBuildRule createFakeBuildRule(
      String target,
      SourcePathResolver resolver,
      BuildRule... deps) {
    return new FakeBuildRule(
        new FakeBuildRuleParamsBuilder(BuildTargetFactory.newInstance(target))
            .setDeps(ImmutableSortedSet.copyOf(deps))
            .build(),
        resolver);
  }

  @Test
  public void getCompilerFailsIfNothingSet() {
    // Setup an empty thrift buck config, missing the compiler.
    FakeBuckConfig buckConfig = new FakeBuckConfig();
    ThriftBuckConfig thriftBuckConfig = new ThriftBuckConfig(buckConfig);

    // Now try to lookup the compiler, which should fail since nothing was set.
    try {
      thriftBuckConfig.getCompiler(ThriftLibraryDescription.CompilerType.THRIFT);
      fail("expected to throw");
    } catch (HumanReadableException e) {
      assertTrue(
          e.getMessage(),
          e.getMessage().contains(".buckconfig: thrift:compiler must be set"));
    }
  }

  @Test
  public void getCompilerSucceedsIfJustCompilerPathIsSet() throws IOException {
    BuildRuleResolver resolver = new BuildRuleResolver();
    Path thriftPath = Paths.get("thrift_path");
    FakeProjectFilesystem filesystem = new FakeProjectFilesystem();
    filesystem.touch(thriftPath);

    // Setup an empty thrift buck config, missing the compiler.
    FakeBuckConfig buckConfig = new FakeBuckConfig(
        ImmutableMap.of(
            "thrift", ImmutableMap.of("compiler", thriftPath.toString())),
        filesystem);
    ThriftBuckConfig thriftBuckConfig = new ThriftBuckConfig(buckConfig);

    // Now try to lookup the compiler, which should succeed.
    SourcePath compiler =
        thriftBuckConfig.getCompiler(ThriftLibraryDescription.CompilerType.THRIFT);

    // Verify that the returned SourcePath wraps the compiler path correctly.
    assertTrue(compiler instanceof PathSourcePath);
    assertEquals(
        thriftPath,
        new SourcePathResolver(resolver).getPath(compiler));
  }

  @Test
  public void getCompilerSucceedsIfJustCompilerTargetIsSet() {
    BuildRuleResolver resolver = new BuildRuleResolver();
    SourcePathResolver pathResolver = new SourcePathResolver(resolver);
    BuildRule thriftRule = createFakeBuildRule("//thrift:target", pathResolver);
    BuildTarget thriftTarget = thriftRule.getBuildTarget();

    // Add the thrift rule to the resolver.
    resolver.addToIndex(thriftRule);

    // Setup an empty thrift buck config, missing the compiler.
    FakeBuckConfig buckConfig = new FakeBuckConfig(
        ImmutableMap.of(
            "thrift", ImmutableMap.of("compiler", thriftTarget.toString())));
    ThriftBuckConfig thriftBuckConfig = new ThriftBuckConfig(buckConfig);

    // Now try to lookup the compiler, which should succeed.
    SourcePath compiler =
        thriftBuckConfig.getCompiler(ThriftLibraryDescription.CompilerType.THRIFT);

    // Verify that the returned SourcePath wraps the compiler path correctly.
    assertTrue(compiler instanceof BuildTargetSourcePath);
    assertEquals(
        BuildTargetFactory.newInstance("//thrift:target"),
        ((BuildTargetSourcePath) compiler).getTarget());
  }

  @Test
  public void getCompilerThriftVsThrift2() throws IOException {
    ProjectFilesystem filesystem = new AllExistingProjectFilesystem();

    // Setup an empty thrift buck config with thrift and thrift2 set..
    FakeBuckConfig buckConfig = new FakeBuckConfig(
        ImmutableMap.of(
            "thrift",
            ImmutableMap.of(
                "compiler", "thrift1",
                "compiler2", "thrift2")),
        filesystem);
    ThriftBuckConfig thriftBuckConfig = new ThriftBuckConfig(buckConfig);

    // Verify that thrift1 and thrift2 are selected correctly.
    assertEquals(
        new TestSourcePath("thrift1"),
        thriftBuckConfig.getCompiler(ThriftLibraryDescription.CompilerType.THRIFT));
    assertEquals(
        new TestSourcePath("thrift2"),
        thriftBuckConfig.getCompiler(ThriftLibraryDescription.CompilerType.THRIFT2));
  }

  @Test
  public void getCompilerThrift2FallsbackToThrift() throws IOException {
    ProjectFilesystem filesystem = new AllExistingProjectFilesystem();

    // Setup an empty thrift buck config with thrift and thrift2 set..
    FakeBuckConfig buckConfig = new FakeBuckConfig(
        ImmutableMap.of(
            "thrift",
            ImmutableMap.of("compiler", "thrift1")),
        filesystem);
    ThriftBuckConfig thriftBuckConfig = new ThriftBuckConfig(buckConfig);

    // Verify that thrift2 falls back to the setting of thrift1.
    assertEquals(
        new TestSourcePath("thrift1"),
        thriftBuckConfig.getCompiler(ThriftLibraryDescription.CompilerType.THRIFT2));
  }

}
