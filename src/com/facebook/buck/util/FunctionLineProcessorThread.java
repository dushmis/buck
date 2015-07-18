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

package com.facebook.buck.util;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;

import java.io.InputStream;
import java.io.OutputStream;

public class FunctionLineProcessorThread extends LineProcessorThread {

  private final Function<String, Iterable<String>> processor;

  public FunctionLineProcessorThread(
      InputStream inputStream,
      OutputStream outputStream,
      Function<String, Iterable<String>> processor) {
    super(inputStream, outputStream);
    this.processor = Preconditions.checkNotNull(processor);
  }

  @Override
  public Iterable<String> process(String line) {
    return processor.apply(line);
  }

}
