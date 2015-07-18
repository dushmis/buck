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

package exotest;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;

import com.facebook.buck.android.support.exopackage.ExopackageSoLoader;

public class LogActivity extends Activity {

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);

    Log.i("EXOPACKAGE_TEST", "VALUE=" + Value.VALUE);

    ExopackageSoLoader.loadLibrary("one");
    ExopackageSoLoader.loadLibrary("two");

    Log.i("EXOPACKAGE_TEST", "NATIVE_ONE=" + stringOneFromJNI());
    Log.i("EXOPACKAGE_TEST", "NATIVE_TWO=" + stringTwoFromJNI());

    finish();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();

    // Workaround for the fact that that "am force-stop" doesn't work on Gingerbread.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      Process.killProcess(Process.myPid());
    }
  }

  public native String stringOneFromJNI();
  public native String stringTwoFromJNI();
}
