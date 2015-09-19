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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.facebook.buck.cli.FakeBuckConfig;
import com.facebook.buck.cxx.CxxBuckConfig;
import com.facebook.buck.cxx.CxxPlatform;
import com.facebook.buck.cxx.DefaultCxxPlatforms;
import com.facebook.buck.io.AlwaysFoundExecutableFinder;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.model.Flavor;
import com.facebook.buck.model.FlavorDomain;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.BuildTargetSourcePath;
import com.facebook.buck.rules.FakeBuildContext;
import com.facebook.buck.rules.FakeBuildRuleParamsBuilder;
import com.facebook.buck.rules.FakeBuildableContext;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.TestSourcePath;
import com.facebook.buck.shell.Genrule;
import com.facebook.buck.shell.GenruleBuilder;
import com.facebook.buck.step.Step;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;

import org.hamcrest.Matchers;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

public class PythonBinaryDescriptionTest {

  private static final PythonBuckConfig PYTHON_BUCK_CONFIG =
      new PythonBuckConfig(
          new FakeBuckConfig(),
          new AlwaysFoundExecutableFinder());
  private static final PythonEnvironment PYTHON_ENV =
      new PythonEnvironment(
          Paths.get("python"),
          PythonVersion.of("2.6"));
  private static final CxxPlatform CXX_PLATFORM = DefaultCxxPlatforms.build(
      new CxxBuckConfig(new FakeBuckConfig()));
  private static final FlavorDomain<CxxPlatform> CXX_PLATFORMS =
      new FlavorDomain<>("platform", ImmutableMap.<Flavor, CxxPlatform>of());

  @Test
  public void thatComponentSourcePathDepsPropagateProperly() {
    BuildRuleResolver resolver = new BuildRuleResolver();

    Genrule genrule = (Genrule) GenruleBuilder
        .newGenruleBuilder(BuildTargetFactory.newInstance("//:gen"))
        .setOut("blah.py")
        .build(resolver);
    BuildRuleParams libParams = new FakeBuildRuleParamsBuilder("//:lib").build();
    PythonLibrary lib = new PythonLibrary(
        libParams,
        new SourcePathResolver(resolver),
        ImmutableMap.<Path, SourcePath>of(
            Paths.get("hello"),
            new BuildTargetSourcePath(genrule.getBuildTarget())),
        ImmutableMap.<Path, SourcePath>of(),
        Optional.<Boolean>absent());

    BuildRuleParams params =
        new FakeBuildRuleParamsBuilder(BuildTargetFactory.newInstance("//:bin"))
            .setDeps(ImmutableSortedSet.<BuildRule>of(lib))
            .build();
    PythonBinaryDescription desc =
        new PythonBinaryDescription(
            PYTHON_BUCK_CONFIG,
            PYTHON_ENV,
            CXX_PLATFORM,
            CXX_PLATFORMS);
    PythonBinaryDescription.Arg arg = desc.createUnpopulatedConstructorArg();
    arg.deps = Optional.of(ImmutableSortedSet.<BuildTarget>of());
    arg.mainModule = Optional.absent();
    arg.main = Optional.<SourcePath>of(new TestSourcePath("blah.py"));
    arg.baseModule = Optional.absent();
    arg.zipSafe = Optional.absent();
    arg.buildArgs = Optional.absent();
    BuildRule rule = desc.createBuildRule(TargetGraph.EMPTY, params, resolver, arg);

    assertEquals(
        ImmutableSortedSet.<BuildRule>of(genrule),
        rule.getDeps());
  }

  @Test
  public void thatMainSourcePathPropagatesToDeps() {
    BuildRuleResolver resolver = new BuildRuleResolver();

    Genrule genrule = (Genrule) GenruleBuilder
        .newGenruleBuilder(BuildTargetFactory.newInstance("//:gen"))
        .setOut("blah.py")
        .build(resolver);
    BuildRuleParams params = new FakeBuildRuleParamsBuilder("//:bin").build();
    PythonBinaryDescription desc =
        new PythonBinaryDescription(
            PYTHON_BUCK_CONFIG,
            PYTHON_ENV,
            CXX_PLATFORM,
            CXX_PLATFORMS);
    PythonBinaryDescription.Arg arg = desc.createUnpopulatedConstructorArg();
    arg.deps = Optional.of(ImmutableSortedSet.<BuildTarget>of());
    arg.mainModule = Optional.absent();
    arg.main =
        Optional.<SourcePath>of(
            new BuildTargetSourcePath(genrule.getBuildTarget()));
    arg.baseModule = Optional.absent();
    arg.zipSafe = Optional.absent();
    arg.buildArgs = Optional.absent();
    BuildRule rule = desc.createBuildRule(TargetGraph.EMPTY, params, resolver, arg);
    assertEquals(
        ImmutableSortedSet.<BuildRule>of(genrule),
        rule.getDeps());
  }

  @Test
  public void baseModule() {
    BuildRuleResolver resolver = new BuildRuleResolver();

    BuildTarget target = BuildTargetFactory.newInstance("//foo:bin");
    BuildRuleParams params = new FakeBuildRuleParamsBuilder(target).build();
    String mainName = "main.py";
    PythonBinaryDescription desc =
        new PythonBinaryDescription(
            PYTHON_BUCK_CONFIG,
            PYTHON_ENV,
            CXX_PLATFORM,
            CXX_PLATFORMS);
    PythonBinaryDescription.Arg arg = desc.createUnpopulatedConstructorArg();
    arg.deps = Optional.of(ImmutableSortedSet.<BuildTarget>of());
    arg.mainModule = Optional.absent();
    arg.main = Optional.<SourcePath>of(new TestSourcePath("foo/" + mainName));
    arg.zipSafe = Optional.absent();
    arg.buildArgs = Optional.absent();

    // Run without a base module set and verify it defaults to using the build target
    // base name.
    arg.baseModule = Optional.absent();
    PythonBinary normalRule = desc
        .createBuildRule(TargetGraph.EMPTY, params, resolver, arg);
    assertEquals(
        PythonUtil.toModuleName(target, target.getBasePath().resolve(mainName).toString()),
        normalRule.getMainModule());

    // Run *with* a base module set and verify it gets used to build the main module path.
    arg.baseModule = Optional.of("blah");
    PythonBinary baseModuleRule = desc
        .createBuildRule(TargetGraph.EMPTY, params, resolver, arg);
    assertEquals(
        PythonUtil.toModuleName(
            target,
            Paths.get(arg.baseModule.get()).resolve(mainName).toString()),
        baseModuleRule.getMainModule());
  }

  @Test
  public void mainModule() {
    BuildRuleResolver resolver = new BuildRuleResolver();
    BuildTarget target = BuildTargetFactory.newInstance("//foo:bin");
    BuildRuleParams params = new FakeBuildRuleParamsBuilder(target).build();
    String mainModule = "foo.main";
    PythonBinaryDescription desc =
        new PythonBinaryDescription(
            PYTHON_BUCK_CONFIG,
            PYTHON_ENV,
            CXX_PLATFORM,
            CXX_PLATFORMS);
    PythonBinaryDescription.Arg arg = desc.createUnpopulatedConstructorArg();
    arg.deps = Optional.of(ImmutableSortedSet.<BuildTarget>of());
    arg.mainModule = Optional.of(mainModule);
    arg.main = Optional.absent();
    arg.baseModule = Optional.absent();
    arg.zipSafe = Optional.absent();
    arg.buildArgs = Optional.absent();
    PythonBinary rule = desc
        .createBuildRule(TargetGraph.EMPTY, params, resolver, arg);
    assertEquals(mainModule, rule.getMainModule());
  }

  @Test
  public void pexExtension() {
    BuildRuleResolver resolver = new BuildRuleResolver();
    BuildTarget target = BuildTargetFactory.newInstance("//foo:bin");
    BuildRuleParams params = new FakeBuildRuleParamsBuilder(target).build();
    PythonBuckConfig config =
        new PythonBuckConfig(
            new FakeBuckConfig(
                ImmutableMap.of(
                    "python",
                    ImmutableMap.of("pex_extension", ".different_extension"))),
            new AlwaysFoundExecutableFinder());
    PythonBinaryDescription desc =
        new PythonBinaryDescription(
            config,
            PYTHON_ENV,
            CXX_PLATFORM,
            CXX_PLATFORMS);
    PythonBinaryDescription.Arg arg = desc.createUnpopulatedConstructorArg();
    arg.deps = Optional.of(ImmutableSortedSet.<BuildTarget>of());
    arg.mainModule = Optional.of("main");
    arg.main = Optional.absent();
    arg.baseModule = Optional.absent();
    arg.zipSafe = Optional.absent();
    arg.buildArgs = Optional.absent();
    PythonBinary rule = desc
        .createBuildRule(TargetGraph.EMPTY, params, resolver, arg);
    assertThat(
        Preconditions.checkNotNull(rule.getPathToOutput()).toString(),
        Matchers.endsWith(".different_extension"));
  }

  @Test
  public void buildArgs() {
    BuildTarget target = BuildTargetFactory.newInstance("//foo:bin");
    BuildRuleResolver resolver = new BuildRuleResolver();
    ImmutableList<String> buildArgs = ImmutableList.of("--some", "--args");
    PythonBinary binary =
        (PythonBinary) PythonBinaryBuilder.create(target)
            .setMainModule("main")
            .setBuildArgs(buildArgs)
            .build(resolver);
    ImmutableList<Step> buildSteps =
        binary.getBuildSteps(FakeBuildContext.NOOP_CONTEXT, new FakeBuildableContext());
    PexStep pexStep = FluentIterable.from(buildSteps)
        .filter(PexStep.class)
        .get(0);
    assertThat(
        pexStep.getCommandPrefix(),
        Matchers.hasItems(buildArgs.toArray(new String[buildArgs.size()])));
  }

}
