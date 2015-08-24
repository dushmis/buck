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

package com.facebook.buck.rules;

import com.facebook.buck.android.AndroidDirectoryResolver;
import com.facebook.buck.cli.BuckConfig;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetException;
import com.facebook.buck.parser.ParserConfig;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Represents a single checkout of a code base. Two repositories model the same code base if their
 * underlying {@link ProjectFilesystem}s are equal.
 */
public class Repository {

  private final Optional<String> name;
  private final ProjectFilesystem filesystem;
  private final BuckConfig config;
  private final KnownBuildRuleTypes knownBuildRuleTypes;
  private final AndroidDirectoryResolver directoryResolver;

  public Repository(
      Optional<String> name,
      ProjectFilesystem filesystem,
      BuckConfig config,
      KnownBuildRuleTypes knownBuildRuleTypes,
      AndroidDirectoryResolver directoryResolver) {

    this.name = name;
    this.filesystem = filesystem;
    this.config = config;
    this.knownBuildRuleTypes = knownBuildRuleTypes;
    this.directoryResolver = directoryResolver;
  }

  public Optional<String> getName() {
    return name;
  }

  public ProjectFilesystem getFilesystem() {
    return filesystem;
  }

  public KnownBuildRuleTypes getKnownBuildRuleTypes() {
    return knownBuildRuleTypes;
  }

  public BuckConfig getBuckConfig() {
    return config;
  }

  // TODO(jacko): This is a hack to avoid breaking the build. Get rid of it.
  public AndroidDirectoryResolver getAndroidDirectoryResolver() {
    return directoryResolver;
  }

  public Description<?> getDescription(BuildRuleType type) {
    return getKnownBuildRuleTypes().getDescription(type);
  }

  public BuildRuleType getBuildRuleType(String rawType) {
    return getKnownBuildRuleTypes().getBuildRuleType(rawType);
  }

  public ImmutableSet<Description<?>> getAllDescriptions() {
    return getKnownBuildRuleTypes().getAllDescriptions();
  }

  public Path getAbsolutePathToBuildFile(BuildTarget target)
      throws MissingBuildFileException {
    Preconditions.checkArgument(
        target.getRepository().equals(getName()),
        "Target %s is not from this repository %s.",
        target,
        getName());
    Path relativePath = target.getBasePath().resolve(
        new ParserConfig(getBuckConfig()).getBuildFileName());
    if (!getFilesystem().isFile(relativePath)) {
      throw new MissingBuildFileException(target, getBuckConfig());
    }
    return getFilesystem().resolve(relativePath);
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Repository that = (Repository) o;
    return Objects.equals(filesystem, that.filesystem) &&
        Objects.equals(config, that.config) &&
        Objects.equals(directoryResolver, that.directoryResolver);
  }

  @Override
  public int hashCode() {
    return Objects.hash(filesystem, config, directoryResolver);
  }

  @SuppressWarnings("serial")
  public static class MissingBuildFileException extends BuildTargetException {
    public MissingBuildFileException(BuildTarget buildTarget, BuckConfig buckConfig) {
      super(String.format("No build file at %s when resolving target %s.",
          buildTarget.getBasePathWithSlash() + new ParserConfig(buckConfig).getBuildFileName(),
          buildTarget.getFullyQualifiedName()));
    }

    @Override
    public String getHumanReadableErrorMessage() {
      return getMessage();
    }
  }
}
