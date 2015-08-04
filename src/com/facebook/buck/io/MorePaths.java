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

package com.facebook.buck.io;

import com.facebook.buck.util.BuckConstant;
import com.facebook.buck.util.environment.Platform;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.io.ByteSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.annotation.Nullable;

public class MorePaths {

  /** Utility class: do not instantiate. */
  private MorePaths() {}

  public static final Function<String, Path> TO_PATH = new Function<String, Path>() {
    @Override
    public Path apply(String path) {
      return Paths.get(path);
    }
  };

  private static final Path EMPTY_PATH = Paths.get("");

  public static String pathWithUnixSeparators(String path) {
    return pathWithUnixSeparators(Paths.get(path));
  }

  public static String pathWithUnixSeparators(Path path) {
    return path.toString().replace("\\", "/");
  }

  public static String pathWithPlatformSeparators(String path) {
    return pathWithPlatformSeparators(Paths.get(path));
  }

  public static String pathWithPlatformSeparators(Path path) {
    if (Platform.detect() == Platform.WINDOWS) {
      return path.toString().replace("/", "\\");
    } else {
      return pathWithUnixSeparators(path);
    }
  }

  public static String pathWithUnixSeparatorsAndTrailingSlash(Path path) {
    return pathWithUnixSeparators(path) + "/";
  }

  /**
   * @param toMakeAbsolute The {@link Path} to act upon.
   * @return The Path, made absolute and normalized.
   */
  public static Path absolutify(Path toMakeAbsolute) {
    return toMakeAbsolute.toAbsolutePath().normalize();
  }

  public static Path getParentOrEmpty(Path path) {
    Path parent = path.getParent();
    if (parent == null) {
      parent = EMPTY_PATH;
    }
    return parent;
  }

  /**
   * Get the path of a file relative to a base directory.
   *
   * @param path must reference a file, not a directory.
   * @param baseDir must reference a directory that is relative to a common directory with the path.
   *     may be null if referencing the same directory as the path.
   * @return the relative path of path from the directory baseDir.
   */
  public static Path getRelativePath(Path path, @Nullable Path baseDir) {
    if (baseDir == null) {
      // This allows callers to use this method with "file.parent()" for files from the project
      // root dir.
      baseDir = EMPTY_PATH;
    }
    Preconditions.checkArgument(!path.isAbsolute(),
        "Path must be relative: %s.", path);
    Preconditions.checkArgument(!baseDir.isAbsolute(),
        "Path must be relative: %s.", baseDir);
    return relativize(baseDir, path);
  }

  /**
   * Get a relative path from path1 to path2, first normalizing each path.
   *
   * This method is a workaround for JDK-6925169 (Path.relativize
   * returns incorrect result if path contains "." or "..").
   */
  public static Path relativize(Path path1, Path path2) {
    Preconditions.checkArgument(
        path1.isAbsolute() == path2.isAbsolute(),
        "Both paths must be absolute or both paths must be relative. (%s is %s, %s is %s)",
        path1,
        path1.isAbsolute() ? "absolute" : "relative",
        path2,
        path2.isAbsolute() ? "absolute" : "relative");

    path1 = normalize(path1);
    path2 = normalize(path2);

    // On Windows, if path1 is "" then Path.relativize returns ../path2 instead of path2 or ./path2
    if (EMPTY_PATH.equals(path1)) {
      return path2;
    }
    return path1.relativize(path2);
  }

  /**
   * Get a path without unnecessary path parts.
   *
   * This method is a workaround for JDK-8037945 (Paths.get("").normalize() throws
   * ArrayIndexOutOfBoundsException).
   */
  public static Path normalize(Path path) {
    if (!EMPTY_PATH.equals(path)) {
      path = path.normalize();
    }
    return path;
  }

  /**
   * Creates a symlink at
   * {@code projectFilesystem.getRootPath().resolve(pathToDesiredLinkUnderProjectRoot)} that
   * points to {@code projectFilesystem.getRootPath().resolve(pathToExistingFileUnderProjectRoot)}
   * using a relative symlink.
   *
   * @param pathToDesiredLinkUnderProjectRoot must reference a file, not a directory.
   * @param pathToExistingFileUnderProjectRoot must reference a file, not a directory.
   * @return the relative path from the new symlink that was created to the existing file.
   */
  public static Path createRelativeSymlink(
      Path pathToDesiredLinkUnderProjectRoot,
      Path pathToExistingFileUnderProjectRoot,
      ProjectFilesystem projectFilesystem) throws IOException {
    return createRelativeSymlink(
        pathToDesiredLinkUnderProjectRoot,
        pathToExistingFileUnderProjectRoot,
        projectFilesystem.getRootPath());
  }

  /**
   * Creates a symlink at {@code pathToProjectRoot.resolve(pathToDesiredLinkUnderProjectRoot)} that
   * points to {@code pathToProjectRoot.resolve(pathToExistingFileUnderProjectRoot)} using a
   * relative symlink. Both params must be relative to the project root.
   *
   * @param pathToDesiredLinkUnderProjectRoot must reference a file, not a directory.
   * @param pathToExistingFileUnderProjectRoot must reference a file, not a directory.
   * @return the relative path from the new symlink that was created to the existing file.
   */
  public static Path createRelativeSymlink(
      Path pathToDesiredLinkUnderProjectRoot,
      Path pathToExistingFileUnderProjectRoot,
      Path pathToProjectRoot) throws IOException {
    Path target = getRelativePath(
        pathToExistingFileUnderProjectRoot,
        pathToDesiredLinkUnderProjectRoot.getParent());
    Files.createSymbolicLink(pathToProjectRoot.resolve(pathToDesiredLinkUnderProjectRoot), target);
    return target;
  }

  /**
   * Convert a set of input file paths as strings to {@link Path} objects.
   */
  public static ImmutableSortedSet<Path> asPaths(Iterable<String> paths) {
    ImmutableSortedSet.Builder<Path> builder = ImmutableSortedSet.naturalOrder();
    for (String path : paths) {
      builder.add(TO_PATH.apply(path));
    }
    return builder.build();
  }

  /**
   * Filters out {@link Path} objects from {@code paths} that aren't a subpath of {@code root} and
   * returns a set of paths relative to {@code root}.
   */
  public static ImmutableSet<Path> filterForSubpaths(Iterable<Path> paths, final Path root) {
    final Path normalizedRoot = root.toAbsolutePath().normalize();
    return FluentIterable.from(paths)
        .filter(new Predicate<Path>() {
          @Override
          public boolean apply(Path input) {
            if (input.isAbsolute()) {
              return input.normalize().startsWith(normalizedRoot);
            } else {
              return true;
            }
          }
        })
        .transform(new Function<Path, Path>() {
          @Override
          public Path apply(Path input) {
            if (input.isAbsolute()) {
              return relativize(normalizedRoot, input);
            } else {
              return input;
            }
          }
        })
        .toSet();
  }

  /**
   * @return Whether the input path directs to a file in the buck generated files folder.
   */
  public static boolean isGeneratedFile(Path pathRelativeToProjectRoot) {
    return pathRelativeToProjectRoot.startsWith(BuckConstant.GEN_PATH);
  }

  /**
   * Expands "~/foo" into "/home/zuck/foo". Returns regular paths unmodified.
   */
  public static Path expandHomeDir(Path path) {
    if (!path.startsWith("~")) {
      return path;
    }
    Path homePath = Paths.get(System.getProperty("user.home"));
    if (path.equals(Paths.get("~"))) {
      return homePath;
    }
    return homePath.resolve(path.subpath(1, path.getNameCount()));
  }

  public static boolean fileContentsDiffer(
      InputStream contents,
      Path path,
      ProjectFilesystem projectFilesystem) throws IOException {
    try {
      // Hash the contents of the file at path so we don't have to pull the whole thing into memory.
      MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
      byte[] pathDigest;
      try (InputStream is = projectFilesystem.newFileInputStream(path)) {
          pathDigest = inputStreamDigest(is, sha1);
      }
      // Hash 'contents' and see if the two differ.
      sha1.reset();
      byte[] contentsDigest = inputStreamDigest(contents, sha1);
      return !Arrays.equals(pathDigest, contentsDigest);
    } catch (NoSuchFileException e) {
      // If the file doesn't exist, we need to create it.
      return true;
    } catch (NoSuchAlgorithmException e) {
      throw Throwables.propagate(e);
    }
  }

  public static ByteSource asByteSource(final Path path) {
    return new ByteSource() {
      @Override
      public InputStream openStream() throws IOException {
        return Files.newInputStream(path);
      }
    };
  }

  private static byte[] inputStreamDigest(InputStream inputStream, MessageDigest messageDigest)
      throws IOException {
    try (DigestInputStream dis = new DigestInputStream(inputStream, messageDigest)) {
      byte[] buf = new byte[4096];
      while (true) {
        // Read the contents of the existing file so we can hash it.
        if (dis.read(buf) == -1) {
          break;
        }
      }
      return dis.getMessageDigest().digest();
    }
  }

  public static String getFileExtension(Path path) {
    String name = path.getFileName().toString();
    int index = name.lastIndexOf('.');
    return index == -1 ? "" : name.substring(index + 1);
  }

  public static String getNameWithoutExtension(Path file) {
    String name = file.getFileName().toString();
    int index = name.lastIndexOf('.');
    return index == -1 ? name : name.substring(0, index);
  }


  public static void append(Path path, String text, Charset charset) throws IOException{
    try (Writer writer = Files.newBufferedWriter(path, charset)) {
      writer.append(text);
      writer.flush();
    }
  }
}
