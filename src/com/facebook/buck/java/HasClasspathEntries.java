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

package com.facebook.buck.java;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;

import java.nio.file.Path;

/**
 * Implemented by build rules where the output has a classpath environment.
 */
public interface HasClasspathEntries {

  /**
   * @return A map of rule names to classpath entries for this rule and its dependencies.
   */
  ImmutableSetMultimap<JavaLibrary, Path> getTransitiveClasspathEntries();

  /**
   * @return A set of rules contributing classpath entries for this rule and its dependencies.
   */
  ImmutableSet<JavaLibrary> getTransitiveClasspathDeps();

}
