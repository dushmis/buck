/*
 * Copyright 2014-present Facebook, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may
 *  not use this file except in compliance with the License. You may obtain
 *  a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  License for the specific language governing permissions and limitations
 *  under the License.
 */

package com.facebook.buck.android;

import com.android.common.SdkConstants;
import com.facebook.buck.android.NdkCxxPlatforms.TargetCpuType;
import com.facebook.buck.cxx.StripStep;
import com.facebook.buck.io.MorePaths;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.model.Pair;
import com.facebook.buck.rules.AbstractBuildRule;
import com.facebook.buck.rules.AddToRuleKey;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildableContext;
import com.facebook.buck.rules.RuleKey;
import com.facebook.buck.rules.RuleKeyAppendable;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.step.AbstractExecutionStep;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.CopyStep;
import com.facebook.buck.step.fs.MakeCleanDirectoryStep;
import com.facebook.buck.step.fs.MkdirStep;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * A {@link com.facebook.buck.rules.BuildRule} that gathers shared objects generated by
 * {@code ndk_library} and {@code prebuilt_native_library} rules into a directory. It also hashes
 * the shared objects collected and stores this metadata in a text file, to be used later by
 * {@link ExopackageInstaller}.
 */
public class CopyNativeLibraries extends AbstractBuildRule implements RuleKeyAppendable {

  private final ImmutableSet<SourcePath> nativeLibDirectories;
  @AddToRuleKey
  private final ImmutableSet<TargetCpuType> cpuFilters;
  /**
   * A map of native libraries to copy in which are already filtered using the above CPU filter.
   * The keys of the map are the tuple of {@link TargetCpuType} and shared library SONAME
   * (e.g. <"x86", "libtest.so">), and the values of the map are the full paths to the libraries.
   */
  private final ImmutableMap<TargetCpuType, NdkCxxPlatform> nativePlatforms;
  private final ImmutableMap<Pair<TargetCpuType, String>, SourcePath> filteredNativeLibraries;

  protected CopyNativeLibraries(
      BuildRuleParams buildRuleParams,
      SourcePathResolver resolver,
      ImmutableSet<SourcePath> nativeLibDirectories,
      ImmutableSet<TargetCpuType> cpuFilters,
      ImmutableMap<TargetCpuType, NdkCxxPlatform> nativePlatforms,
      ImmutableMap<Pair<TargetCpuType, String>, SourcePath> filteredNativeLibraries) {
    super(buildRuleParams, resolver);
    this.nativeLibDirectories = nativeLibDirectories;
    this.cpuFilters = cpuFilters;
    this.filteredNativeLibraries = filteredNativeLibraries;
    this.nativePlatforms = nativePlatforms;
    Preconditions.checkArgument(
        !nativeLibDirectories.isEmpty() || !filteredNativeLibraries.isEmpty(),
        "There should be at least one native library to copy.");
  }

  public Path getPathToNativeLibsDir() {
    return getBinPath().resolve("libs");
  }

  public Path getPathToMetadataTxt() {
    return getBinPath().resolve("metadata.txt");
  }

  private Path getBinPath() {
    return BuildTargets.getScratchPath(getBuildTarget(), "__native_libs_%s__");
  }

  @VisibleForTesting
  ImmutableSet<SourcePath> getNativeLibDirectories() {
    return nativeLibDirectories;
  }

  @VisibleForTesting
  ImmutableMap<Pair<TargetCpuType, String>, SourcePath> getFilteredNativeLibraries() {
    return filteredNativeLibraries;
  }

  @Override
  public RuleKey.Builder appendToRuleKey(RuleKey.Builder builder) {
    // Hash in the pre-filtered native libraries we're pulling in.
    ImmutableSortedMap<Pair<TargetCpuType, String>, SourcePath> sortedLibs =
        ImmutableSortedMap.<Pair<TargetCpuType, String>, SourcePath>orderedBy(
            Pair.<TargetCpuType, String>comparator())
            .putAll(filteredNativeLibraries)
            .build();
    for (Map.Entry<Pair<TargetCpuType, String>, SourcePath> entry : sortedLibs.entrySet()) {
      Pair<TargetCpuType, String> entryKey = entry.getKey();
      builder.setReflectively(
          String.format("lib(%s, %s)", entryKey.getFirst(), entryKey.getSecond()),
          entry.getValue());
    }

    return builder;
  }

  @Override
  public ImmutableList<Step> getBuildSteps(
      BuildContext context,
      BuildableContext buildableContext) {
    ImmutableList.Builder<Step> steps = ImmutableList.builder();

    final Path pathToNativeLibs = getPathToNativeLibsDir();
    steps.add(new MakeCleanDirectoryStep(pathToNativeLibs));

    for (SourcePath nativeLibDir : nativeLibDirectories.asList().reverse()) {
      copyNativeLibrary(getResolver().getPath(nativeLibDir), pathToNativeLibs, cpuFilters, steps);
    }

    // Copy in the pre-filtered native libraries.
    for (Map.Entry<Pair<TargetCpuType, String>, SourcePath> entry :
         filteredNativeLibraries.entrySet()) {
      Optional<String> abiDirectoryComponent = getAbiDirectoryComponent(entry.getKey().getFirst());
      Preconditions.checkState(abiDirectoryComponent.isPresent());
      Path destination =
          pathToNativeLibs
              .resolve(abiDirectoryComponent.get())
              .resolve(entry.getKey().getSecond());
      NdkCxxPlatform platform =
          Preconditions.checkNotNull(nativePlatforms.get(entry.getKey().getFirst()));
      steps.add(new MkdirStep(destination.getParent()));
      steps.add(
          new StripStep(
              platform.getCxxPlatform().getStrip().getCommandPrefix(getResolver()),
              ImmutableList.of("--strip-unneeded"),
              getResolver().getPath(entry.getValue()),
              destination));
    }

    final Path pathToMetadataTxt = getPathToMetadataTxt();
    steps.add(
        new AbstractExecutionStep("hash_native_libs") {
          @Override
          public int execute(ExecutionContext context) {
            ProjectFilesystem filesystem = context.getProjectFilesystem();
            ImmutableList.Builder<String> metadataLines = ImmutableList.builder();
            try {
              for (Path nativeLib : filesystem.getFilesUnderPath(pathToNativeLibs)) {
                String filesha1 = filesystem.computeSha1(nativeLib);
                Path relativePath = pathToNativeLibs.relativize(nativeLib);
                metadataLines.add(String.format("%s %s", relativePath.toString(), filesha1));
              }
              filesystem.writeLinesToPath(metadataLines.build(), pathToMetadataTxt);
            } catch (IOException e) {
              context.logError(e, "There was an error hashing native libraries.");
              return 1;
            }
            return 0;
          }
        });

    buildableContext.recordArtifact(pathToNativeLibs);
    buildableContext.recordArtifact(pathToMetadataTxt);

    return steps.build();
  }

  @Nullable
  @Override
  public Path getPathToOutput() {
    return null;
  }

  public static void copyNativeLibrary(Path sourceDir,
      final Path destinationDir,
      ImmutableSet<TargetCpuType> cpuFilters,
      ImmutableList.Builder<Step> steps) {

    if (cpuFilters.isEmpty()) {
      steps.add(
          CopyStep.forDirectory(
              sourceDir,
              destinationDir,
              CopyStep.DirectoryMode.CONTENTS_ONLY));
    } else {
      for (TargetCpuType cpuType : cpuFilters) {
        Optional<String> abiDirectoryComponent = getAbiDirectoryComponent(cpuType);
        Preconditions.checkState(abiDirectoryComponent.isPresent());

        final Path libSourceDir = sourceDir.resolve(abiDirectoryComponent.get());
        Path libDestinationDir = destinationDir.resolve(abiDirectoryComponent.get());

        final MkdirStep mkDirStep = new MkdirStep(libDestinationDir);
        final CopyStep copyStep = CopyStep.forDirectory(
            libSourceDir,
            libDestinationDir,
            CopyStep.DirectoryMode.CONTENTS_ONLY);
        steps.add(
            new Step() {
              @Override
              public int execute(ExecutionContext context) {
                if (!context.getProjectFilesystem().exists(libSourceDir)) {
                  return 0;
                }
                if (mkDirStep.execute(context) == 0 && copyStep.execute(context) == 0) {
                  return 0;
                }
                return 1;
              }

              @Override
              public String getShortName() {
                return "copy_native_libraries";
              }

              @Override
              public String getDescription(ExecutionContext context) {
                ImmutableList.Builder<String> stringBuilder = ImmutableList.builder();
                stringBuilder.add(String.format("[ -d %s ]", libSourceDir.toString()));
                stringBuilder.add(mkDirStep.getDescription(context));
                stringBuilder.add(copyStep.getDescription(context));
                return Joiner.on(" && ").join(stringBuilder.build());
              }
            });
      }
    }

    // Rename native files named like "*-disguised-exe" to "lib*.so" so they will be unpacked
    // by the Android package installer.  Then they can be executed like normal binaries
    // on the device.
    steps.add(
        new AbstractExecutionStep("rename_native_executables") {
          @Override
          public int execute(ExecutionContext context) {

            ProjectFilesystem filesystem = context.getProjectFilesystem();
            final ImmutableSet.Builder<Path> executablesBuilder = ImmutableSet.builder();
            try {
              filesystem.walkRelativeFileTree(destinationDir, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                      if (file.toString().endsWith("-disguised-exe")) {
                        executablesBuilder.add(file);
                      }
                      return FileVisitResult.CONTINUE;
                    }
                  });
              for (Path exePath : executablesBuilder.build()) {
                Path fakeSoPath = Paths.get(
                    MorePaths.pathWithUnixSeparators(exePath)
                        .replaceAll("/([^/]+)-disguised-exe$", "/lib$1.so"));
                filesystem.move(exePath, fakeSoPath);
              }
            } catch (IOException e) {
              context.logError(e, "Renaming native executables failed.");
              return 1;
            }
            return 0;
          }
        });
  }

  /**
   * Native libraries compiled for different CPU architectures are placed in the
   * respective ABI subdirectories, such as 'armeabi', 'armeabi-v7a', 'x86' and 'mips'.
   * This looks at the cpu filter and returns the correct subdirectory. If cpu filter is
   * not present or not supported, returns Optional.absent();
   */
  private static Optional<String> getAbiDirectoryComponent(TargetCpuType cpuType) {
    String component = null;
    if (cpuType.equals(NdkCxxPlatforms.TargetCpuType.ARM)) {
      component = SdkConstants.ABI_ARMEABI;
    } else if (cpuType.equals(NdkCxxPlatforms.TargetCpuType.ARMV7)) {
      component = SdkConstants.ABI_ARMEABI_V7A;
    } else if (cpuType.equals(NdkCxxPlatforms.TargetCpuType.X86)) {
      component = SdkConstants.ABI_INTEL_ATOM;
    } else if (cpuType.equals(NdkCxxPlatforms.TargetCpuType.MIPS)) {
      component = SdkConstants.ABI_MIPS;
    }
    return Optional.fromNullable(component);
  }
}
