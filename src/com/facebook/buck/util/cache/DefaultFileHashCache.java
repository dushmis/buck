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

package com.facebook.buck.util.cache;

import com.facebook.buck.hashing.PathHashing;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.log.Logger;
import com.facebook.buck.util.HashCodeAndFileType;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.eventbus.Subscribe;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.annotation.Nonnull;

public class DefaultFileHashCache implements ProjectFileHashCache {

  private static final Logger LOG = Logger.get(DefaultFileHashCache.class);

  private final ProjectFilesystem projectFilesystem;

  @VisibleForTesting
  final LoadingCache<Path, HashCodeAndFileType> loadingCache;

  public DefaultFileHashCache(ProjectFilesystem projectFilesystem) {
    this.projectFilesystem = projectFilesystem;

    this.loadingCache = CacheBuilder.newBuilder()
        .build(new CacheLoader<Path, HashCodeAndFileType>() {
          @Override
          public HashCodeAndFileType load(@Nonnull Path path) throws Exception {
            return getHashCodeAndFileType(path);
          }
        });
  }

  private HashCodeAndFileType getHashCodeAndFileType(Path path) throws IOException {
    if (projectFilesystem.isDirectory(path)) {
      return HashCodeAndFileType.of(
          getDirHashCode(path),
          HashCodeAndFileType.Type.DIRECTORY);
    } else {
      return HashCodeAndFileType.of(
          getFileHashCode(path),
          HashCodeAndFileType.Type.FILE);
    }
  }

  private HashCode getFileHashCode(final Path path) throws IOException {
    ByteSource source =
        new ByteSource() {
          @Override
          public InputStream openStream() throws IOException {
            return projectFilesystem.newFileInputStream(path);
          }
        };
    return source.hash(Hashing.sha1());
  }

  private HashCode getDirHashCode(Path path) throws IOException {
    Hasher hasher = Hashing.sha1().newHasher();
    PathHashing.hashPaths(hasher, projectFilesystem, ImmutableSet.of(path));
    return hasher.hash();
  }

  @Override
  public boolean contains(Path path) {
    return loadingCache.getIfPresent(path) != null;
  }

  @Override
  public void invalidate(Path path) {
    loadingCache.invalidate(path);
  }

  @Override
  public void invalidateAll() {
    loadingCache.invalidateAll();
  }

  /**
   * @return The {@link com.google.common.hash.HashCode} of the contents of path.
   */
  @Override
  public HashCode get(Path path) throws IOException {
    Preconditions.checkState(!path.isAbsolute());
    Preconditions.checkState(!projectFilesystem.isIgnored(path));
    HashCode sha1;
    try {
      sha1 = loadingCache.get(path.normalize()).getHashCode();
    } catch (ExecutionException e) {
      Throwables.propagateIfInstanceOf(e.getCause(), IOException.class);
      throw Throwables.propagate(e.getCause());
    }
    return Preconditions.checkNotNull(sha1, "Failed to find a HashCode for %s.", path);
  }

  @Override
  public ProjectFilesystem getFilesystem() {
    return projectFilesystem;
  }

  /**
   * Called when file change events are posted to the file change EventBus to invalidate cached
   * build rules if required. {@link Path}s contained within events must all be relative to the
   * {@link ProjectFilesystem} root.
   */
  @Subscribe
  public synchronized void onFileSystemChange(WatchEvent<?> event) throws IOException {
    if (projectFilesystem.isPathChangeEvent(event)) {
      // Path event, remove the path from the cache as it has been changed, added or deleted.
      final Path path = ((Path) event.context()).normalize();
      LOG.verbose("Invalidating %s", path);
      Iterable<Path> pathsToInvalidate =
          Maps.filterEntries(
              loadingCache.asMap(),
              new Predicate<Map.Entry<Path, HashCodeAndFileType>>() {
                  @Override
                  public boolean apply(Map.Entry<Path, HashCodeAndFileType> entry) {
                    switch (entry.getValue().getType()) {
                      case FILE:
                        return path.equals(entry.getKey());
                      case DIRECTORY:
                        return path.startsWith(entry.getKey());
                    }
                    return false;
                  }
              }
          ).keySet();
      LOG.verbose("Paths to invalidate: %s", pathsToInvalidate);
      loadingCache.invalidateAll(pathsToInvalidate);
    } else {
      // Non-path change event, likely an overflow due to many change events: invalidate everything.
      LOG.debug("Invalidating all");
      loadingCache.invalidateAll();
    }
  }

}
