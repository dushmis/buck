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

package com.facebook.buck.timing;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;

/**
 * Fake implementation of {@link Clock} which returns the last time
 * to which it was set.
 */
public class SettableFakeClock implements Clock {
  private final AtomicLong currentTimeMillis;
  private final AtomicLong nanoTime;

  public SettableFakeClock(long currentTimeMillis, long nanoTime) {
    this.currentTimeMillis = new AtomicLong(currentTimeMillis);
    this.nanoTime = new AtomicLong(nanoTime);
  }

  public void setCurrentTimeMillis(long millis) {
    currentTimeMillis.set(millis);
  }

  public void advanceTimeNanos(long nanos) {
    this.nanoTime.addAndGet(nanos);
    this.currentTimeMillis.addAndGet(TimeUnit.NANOSECONDS.toMillis(nanos));
  }

  @Override
  public long currentTimeMillis() {
    return currentTimeMillis.get();
  }

  @Override
  public long nanoTime() {
    return nanoTime.get();
  }
}
