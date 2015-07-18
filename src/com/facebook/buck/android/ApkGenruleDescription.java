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

package com.facebook.buck.android;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.BuildRuleType;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.InstallableApk;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.macros.ClasspathMacroExpander;
import com.facebook.buck.rules.macros.ExecutableMacroExpander;
import com.facebook.buck.rules.macros.LocationMacroExpander;
import com.facebook.buck.rules.macros.MacroException;
import com.facebook.buck.rules.macros.MacroExpander;
import com.facebook.buck.rules.macros.MacroHandler;
import com.facebook.buck.util.HumanReadableException;
import com.facebook.infer.annotation.SuppressFieldNotInitialized;
import com.google.common.base.Optional;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;

public class ApkGenruleDescription implements Description<ApkGenruleDescription.Arg> {

  public static final BuildRuleType TYPE = BuildRuleType.of("apk_genrule");

  private static final MacroHandler MACRO_HANDLER =
      new MacroHandler(
          ImmutableMap.<String, MacroExpander>of(
              "classpath", new ClasspathMacroExpander(),
              "exe", new ExecutableMacroExpander(),
              "location", new LocationMacroExpander()));

  @Override
  public BuildRuleType getBuildRuleType() {
    return TYPE;
  }

  @Override
  public Arg createUnpopulatedConstructorArg() {
    return new Arg();
  }

  @Override
  public <A extends Arg> ApkGenrule createBuildRule(
      BuildRuleParams params,
      BuildRuleResolver resolver,
      A args) {
    BuildRule installableApk = resolver.getRule(args.apk);
    if (!(installableApk instanceof InstallableApk)) {
      throw new HumanReadableException("The 'apk' argument of %s, %s, must correspond to an " +
          "installable rule, such as android_binary() or apk_genrule().",
          params.getBuildTarget(),
          args.apk.getFullyQualifiedName());
    }

    SourcePathResolver pathResolver = new SourcePathResolver(resolver);

    ImmutableList<SourcePath> srcs = args.srcs.get();
    ImmutableSortedSet<BuildRule> extraDeps =
        ImmutableSortedSet.<BuildRule>naturalOrder()
        .addAll(pathResolver.filterBuildRuleInputs(srcs))
        .add(installableApk)
        .build();

    return new ApkGenrule(
        params
            .copyWithExtraDeps(Suppliers.ofInstance(extraDeps))
            // Attach any extra dependencies found from macro expansion.
            .appendExtraDeps(findExtraDepsFromArgs(params.getBuildTarget(), resolver, args)),
        pathResolver,
        srcs,
        MACRO_HANDLER.getExpander(
            params.getBuildTarget(),
            resolver,
            params.getProjectFilesystem()),
        args.cmd,
        args.bash,
        args.cmdExe,
        params.getPathAbsolutifier(),
        (InstallableApk) installableApk);
  }

  private ImmutableList<BuildRule> findExtraDepsFromArgs(
      BuildTarget target,
      BuildRuleResolver resolver,
      Arg arg) {
    ImmutableList.Builder<BuildRule> deps = ImmutableList.builder();
    try {
      for (String val :
            Optional.presentInstances(ImmutableList.of(arg.bash, arg.cmd, arg.cmdExe))) {
        deps.addAll(MACRO_HANDLER.extractAdditionalBuildTimeDeps(target, resolver, val));
      }
    } catch (MacroException e) {
      throw new HumanReadableException(e, "%s: %s", target, e.getMessage());
    }
    return deps.build();
  }

  @SuppressFieldNotInitialized
  public static class Arg {
    public BuildTarget apk;

    public String out;
    public Optional<String> bash;
    public Optional<String> cmd;
    public Optional<String> cmdExe;
    public Optional<ImmutableList<SourcePath>> srcs;

    public Optional<ImmutableSortedSet<BuildTarget>> deps;
  }
}
