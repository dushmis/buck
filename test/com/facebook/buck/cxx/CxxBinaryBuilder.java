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

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.FlavorDomain;

public class CxxBinaryBuilder extends AbstractCxxSourceBuilder<CxxBinaryDescription.Arg> {

  public CxxBinaryBuilder(
      BuildTarget target,
      CxxBuckConfig cxxBuckConfig,
      CxxPlatform defaultCxxPlatform,
      FlavorDomain<CxxPlatform> cxxPlatforms) {
    super(
        new CxxBinaryDescription(
            cxxBuckConfig,
            defaultCxxPlatform,
            cxxPlatforms,
            CxxPreprocessMode.SEPARATE),
        target);
  }

  public CxxBinaryBuilder(BuildTarget target) {
    this(target, createDefaultConfig(), createDefaultPlatform(), createDefaultPlatforms());
  }

}
