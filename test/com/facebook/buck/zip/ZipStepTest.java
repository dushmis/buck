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

package com.facebook.buck.zip;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.TestExecutionContext;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.testutil.Zip;
import com.facebook.buck.util.environment.Platform;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.archivers.zip.ZipUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Date;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipStepTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private ExecutionContext executionContext;

  @Before
  public void setUp() {
    executionContext = TestExecutionContext.newBuilder()
        .setProjectFilesystem(new ProjectFilesystem(tmp.getRoot().toPath()))
        .build();
  }

  @Test
  public void shouldCreateANewZipFileFromScratch() throws IOException {
    File parent = tmp.newFolder("zipstep");
    File out = new File(parent, "output.zip");

    File toZip = tmp.newFolder("zipdir");
    Files.touch(new File(toZip, "file1.txt"));
    Files.touch(new File(toZip, "file2.txt"));
    Files.touch(new File(toZip, "file3.txt"));

    ZipStep step = new ZipStep(
        Paths.get("zipstep/output.zip"),
        ImmutableSet.<Path>of(),
        false,
        ZipStep.DEFAULT_COMPRESSION_LEVEL,
        Paths.get("zipdir"));
    assertEquals(0, step.execute(executionContext));

    try (Zip zip = new Zip(out, false)) {
      assertEquals(ImmutableSet.of("file1.txt", "file2.txt", "file3.txt"), zip.getFileNames());
    }
  }

  @Test
  public void willOnlyIncludeEntriesInThePathsArgumentIfAnyAreSet() throws IOException {
    File parent = tmp.newFolder("zipstep");
    File out = new File(parent, "output.zip");

    File toZip = tmp.newFolder("zipdir");
    Files.touch(new File(toZip, "file1.txt"));
    Files.touch(new File(toZip, "file2.txt"));
    Files.touch(new File(toZip, "file3.txt"));

    ZipStep step = new ZipStep(
        Paths.get("zipstep/output.zip"),
        ImmutableSet.of(Paths.get("zipdir/file2.txt")),
        false,
        ZipStep.DEFAULT_COMPRESSION_LEVEL,
        Paths.get("zipdir"));
    assertEquals(0, step.execute(executionContext));

    try (Zip zip = new Zip(out, false)) {
      assertEquals(ImmutableSet.of("file2.txt"), zip.getFileNames());
    }
  }

  @Test
  public void willRecurseIntoSubdirectories() throws IOException {
    File parent = tmp.newFolder("zipstep");
    File out = new File(parent, "output.zip");

    File toZip = tmp.newFolder("zipdir");
    Files.touch(new File(toZip, "file1.txt"));
    assertTrue(new File(toZip, "child").mkdir());
    Files.touch(new File(toZip, "child/file2.txt"));

    ZipStep step = new ZipStep(
        Paths.get("zipstep/output.zip"),
        ImmutableSet.<Path>of(),
        false,
        ZipStep.DEFAULT_COMPRESSION_LEVEL,
        Paths.get("zipdir"));
    assertEquals(0, step.execute(executionContext));

    try (Zip zip = new Zip(out, false)) {
      assertEquals(ImmutableSet.of("file1.txt", "child/file2.txt"), zip.getFileNames());
    }
  }

  @Test
  public void mustIncludeTheContentsOfFilesThatAreSymlinked() throws IOException {
    // Symlinks on Windows are _hard_. Let's go shopping.
    assumeTrue(Platform.detect() != Platform.WINDOWS);

    File parent = tmp.newFolder("zipstep");
    File out = new File(parent, "output.zip");
    File target = new File(parent, "target");
    Files.write("example content", target, Charsets.UTF_8);

    File toZip = tmp.newFolder("zipdir");
    Path path = toZip.toPath().resolve("file.txt");
    java.nio.file.Files.createSymbolicLink(path, target.toPath());

    ZipStep step = new ZipStep(
        Paths.get("zipstep/output.zip"),
        ImmutableSet.<Path>of(),
        false,
        ZipStep.DEFAULT_COMPRESSION_LEVEL,
        Paths.get("zipdir"));
    assertEquals(0, step.execute(executionContext));

    try (Zip zip = new Zip(out, false)) {
      assertEquals(ImmutableSet.of("file.txt"), zip.getFileNames());
      byte[] contents = zip.readFully("file.txt");

      assertArrayEquals("example content".getBytes(), contents);
    }
  }

  @Test
  public void overwritingAnExistingZipFileIsAnError() throws IOException {
    File parent = tmp.newFolder("zipstep");
    File out = new File(parent, "output.zip");

    try (Zip zip = new Zip(out, true)) {
      zip.add("file1.txt", "");
    }


    ZipStep step = new ZipStep(
        Paths.get("zipstep"),
        ImmutableSet.<Path>of(),
        false,
        ZipStep.DEFAULT_COMPRESSION_LEVEL,
        Paths.get("zipdir"));
    assertEquals(1, step.execute(executionContext));
  }

  @Test
  public void shouldBeAbleToJunkPaths() throws IOException {
    File parent = tmp.newFolder("zipstep");
    File out = new File(parent, "output.zip");

    File toZip = tmp.newFolder("zipdir");
    assertTrue(new File(toZip, "child").mkdir());
    Files.touch(new File(toZip, "child/file1.txt"));

    ZipStep step = new ZipStep(
        Paths.get("zipstep/output.zip"),
        ImmutableSet.<Path>of(),
        true,
        ZipStep.DEFAULT_COMPRESSION_LEVEL,
        Paths.get("zipdir"));
    assertEquals(0, step.execute(executionContext));

    try (Zip zip = new Zip(out, false)) {
      assertEquals(ImmutableSet.of("file1.txt"), zip.getFileNames());
    }
  }

  @Test
  public void zipWithEmptyDir() throws IOException {
    File parent = tmp.newFolder("zipstep");
    File out = new File(parent, "output.zip");

    tmp.newFolder("zipdir");
    tmp.newFolder("zipdir/foo/");
    tmp.newFolder("zipdir/bar/");

    ZipStep step = new ZipStep(
        Paths.get("zipstep/output.zip"),
        ImmutableSet.<Path>of(),
        true,
        ZipStep.DEFAULT_COMPRESSION_LEVEL,
        Paths.get("zipdir"));
    assertEquals(0, step.execute(executionContext));

    try (Zip zip = new Zip(out, false)) {
      assertEquals(ImmutableSet.of("", "foo/", "bar/"), zip.getDirNames());
    }
  }

  /**
   * Tests a couple bugs:
   *     1) {@link com.facebook.buck.zip.OverwritingZipOutputStream} was writing uncompressed zip
   *        entries incorrectly.
   *     2) {@link ZipStep} wasn't setting the output size when writing uncompressed entries.
   */
  @Test
  public void minCompressionWritesCorrectZipFile() throws IOException {
    File parent = tmp.newFolder("zipstep");
    File out = new File(parent, "output.zip");

    File toZip = tmp.newFolder("zipdir");
    byte[] contents = "hello world".getBytes();
    Files.write(contents, new File(toZip, "file1.txt"));
    Files.write(contents, new File(toZip, "file2.txt"));
    Files.write(contents, new File(toZip, "file3.txt"));

    ZipStep step = new ZipStep(
        Paths.get("zipstep/output.zip"),
        ImmutableSet.<Path>of(),
        false,
        ZipStep.MIN_COMPRESSION_LEVEL,
        Paths.get("zipdir"));
    assertEquals(0, step.execute(executionContext));

    // Use apache's common-compress to parse the zip file, since it reads the central
    // directory and will verify it's valid.
    try (ZipFile zip = new ZipFile(out)) {
      Enumeration<ZipArchiveEntry> entries = zip.getEntries();
      ZipArchiveEntry entry1 = entries.nextElement();
      assertArrayEquals(contents, ByteStreams.toByteArray(zip.getInputStream(entry1)));
      ZipArchiveEntry entry2 = entries.nextElement();
      assertArrayEquals(contents, ByteStreams.toByteArray(zip.getInputStream(entry2)));
      ZipArchiveEntry entry3 = entries.nextElement();
      assertArrayEquals(contents, ByteStreams.toByteArray(zip.getInputStream(entry3)));
    }
  }

  @Test
  public void timesAreSanitized() throws IOException {
    File parent = tmp.newFolder("zipstep");

    // Create a zip file with a file and a directory.
    File toZip = tmp.newFolder("zipdir");
    assertTrue(new File(toZip, "child").mkdir());
    Files.touch(new File(toZip, "child/file.txt"));
    Path outputZip = parent.toPath().resolve("output.zip");
    ZipStep step = new ZipStep(
        outputZip,
        ImmutableSet.<Path>of(),
        false,
        ZipStep.DEFAULT_COMPRESSION_LEVEL,
        Paths.get("zipdir"));
    assertEquals(0, step.execute(executionContext));

    // Iterate over each of the entries, expecting to see all zeros in the time fields.
    assertTrue(java.nio.file.Files.exists(outputZip));
    Date dosEpoch = new Date(ZipUtil.dosToJavaTime(ZipConstants.DOS_EPOCH_START));
    try (ZipInputStream is = new ZipInputStream(new FileInputStream(outputZip.toFile()))) {
      for (ZipEntry entry = is.getNextEntry(); entry != null; entry = is.getNextEntry()) {
        assertEquals(entry.getName(), dosEpoch, new Date(entry.getTime()));
      }
    }
  }

  @Test
  public void zipMaintainsExecutablePermissions() throws IOException {
    assumeTrue(Platform.detect() != Platform.WINDOWS);

    Path parent = tmp.newFolder("zipstep").toPath();
    Path toZip = tmp.newFolder("zipdir").toPath();
    Path file = toZip.resolve("foo.sh");
    ImmutableSet<PosixFilePermission> filePermissions =
        ImmutableSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.OTHERS_READ);
    java.nio.file.Files.createFile(
        file,
        PosixFilePermissions.asFileAttribute(filePermissions));
    Path outputZip = parent.resolve("output.zip");
    ZipStep step = new ZipStep(
        outputZip,
        ImmutableSet.<Path>of(),
        false,
        ZipStep.MIN_COMPRESSION_LEVEL,
        Paths.get("zipdir"));
    assertEquals(0, step.execute(executionContext));

    Path destination = tmp.newFolder("output").toPath();
    Unzip.extractZipFile(outputZip, destination, Unzip.ExistingFileMode.OVERWRITE);
    assertTrue(java.nio.file.Files.isExecutable(destination.resolve("foo.sh")));
  }

  @Test
  public void zipEntryOrderingIsFilesystemAgnostic() throws IOException {
    Path output = Paths.get("output");
    Path zipdir = Paths.get("zipdir");

    // Run the zip step on a filesystem with a particular ordering.
    FakeProjectFilesystem filesystem = new FakeProjectFilesystem();
    ExecutionContext context =
        TestExecutionContext.newBuilder()
            .setProjectFilesystem(filesystem)
            .build();
    filesystem.mkdirs(zipdir);
    filesystem.touch(zipdir.resolve("file1"));
    filesystem.touch(zipdir.resolve("file2"));
    ZipStep step =
        new ZipStep(
            output,
            ImmutableSet.<Path>of(),
            false,
            ZipStep.MIN_COMPRESSION_LEVEL,
            zipdir);
    assertEquals(0, step.execute(context));
    ImmutableList<String> entries1 = getEntries(filesystem, output);

    // Run the zip step on a filesystem with a different ordering.
    filesystem = new FakeProjectFilesystem();
    context =
        TestExecutionContext.newBuilder()
            .setProjectFilesystem(filesystem)
            .build();
    filesystem.mkdirs(zipdir);
    filesystem.touch(zipdir.resolve("file2"));
    filesystem.touch(zipdir.resolve("file1"));
    step =
        new ZipStep(
            output,
            ImmutableSet.<Path>of(),
            false,
            ZipStep.MIN_COMPRESSION_LEVEL,
            zipdir);
    assertEquals(0, step.execute(context));
    ImmutableList<String> entries2 = getEntries(filesystem, output);

    assertEquals(entries1, entries2);
  }

  private ImmutableList<String> getEntries(ProjectFilesystem filesystem, Path zip)
      throws IOException {
    ImmutableList.Builder<String> entries = ImmutableList.builder();
    try (ZipInputStream is = new ZipInputStream(filesystem.newFileInputStream(zip))) {
      for (ZipEntry entry = is.getNextEntry(); entry != null; entry = is.getNextEntry()) {
        entries.add(entry.getName());
      }
    }
    return entries.build();
  }

}
