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

package com.facebook.buck.file;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.AbstractNodeBuilder;
import com.google.common.hash.HashCode;

import java.net.URI;
import java.net.URISyntaxException;

public class RemoteFileBuilder extends AbstractNodeBuilder<RemoteFileDescription.Arg> {
  protected RemoteFileBuilder(Downloader downloader, BuildTarget target) {
    super(new RemoteFileDescription(downloader), target);
  }

  public RemoteFileBuilder setSha1(HashCode hashCode) {
    arg.sha1 = hashCode.toString();
    return this;
  }

  public RemoteFileBuilder setUrl(String url) {
    try {
      arg.url = new URI(url);
    } catch (URISyntaxException e) {
      throw new RuntimeException("Unable to set url: " + url);
    }
    return this;
  }
}
