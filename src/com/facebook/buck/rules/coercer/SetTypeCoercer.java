/*
 * Copyright 2013-present Facebook, Inc.
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

package com.facebook.buck.rules.coercer;

import com.facebook.buck.io.ProjectFilesystem;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;

import java.nio.file.Path;

public class SetTypeCoercer<T> extends CollectionTypeCoercer<ImmutableSet<T>, T> {
  SetTypeCoercer(TypeCoercer<T> elementTypeCoercer) {
    super(elementTypeCoercer);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Class<ImmutableSet<T>> getOutputClass() {
    return (Class<ImmutableSet<T>>) (Class<?>) ImmutableSet.class;
  }

  @Override
  public Optional<ImmutableSet<T>> getOptionalValue() {
    return Optional.of(ImmutableSet.<T>of());
  }

  @Override
  public ImmutableSet<T> coerce(
      ProjectFilesystem filesystem,
      Path pathRelativeToProjectRoot,
      Object object)
      throws CoerceFailedException {
    ImmutableSet.Builder<T> builder = ImmutableSet.builder();
    fill(
        filesystem,
        pathRelativeToProjectRoot,
        builder,
        object);
    return builder.build();
  }
}
