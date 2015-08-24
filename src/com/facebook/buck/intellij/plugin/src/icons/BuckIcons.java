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

package icons;

import com.intellij.openapi.util.IconLoader;

import javax.swing.Icon;

public final class BuckIcons {

  private BuckIcons() {
  }

  private static Icon load(String path) {
    return IconLoader.getIcon(path, BuckIcons.class);
  }

  public static final Icon FILE_TYPE = load("/icons/buck_icon.png"); // 16x16
  public static final Icon BUCK_TOOL_WINDOW_ICON =
      load("/icons/buck_tool_window_icon.png"); // 13x13
}
