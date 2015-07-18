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
package com.facebook.buck.rules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.util.HumanReadableException;

import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

public class BuildTargetSourcePathTest {

  private BuildTarget target = BuildTargetFactory.newInstance("//example:target");

  @Test
  public void shouldThrowAnExceptionIfRuleDoesNotHaveAnOutput() {
    BuildRuleResolver resolver = new BuildRuleResolver();
    SourcePathResolver pathResolver = new SourcePathResolver(resolver);
    BuildRule rule = new FakeBuildRule(target, pathResolver);
    resolver.addToIndex(rule);
    BuildTargetSourcePath path = new BuildTargetSourcePath(rule.getBuildTarget());

    try {
      pathResolver.getPath(path);
      fail();
    } catch (HumanReadableException e) {
      assertEquals("No known output for: " + target.getFullyQualifiedName(), e.getMessage());
    }
  }

  @Test
  public void mustUseProjectFilesystemToResolvePathToFile() {
    BuildRuleResolver resolver = new BuildRuleResolver();
    SourcePathResolver pathResolver = new SourcePathResolver(resolver);
    BuildRule rule = new FakeBuildRule(target, pathResolver) {
      @Override
      public Path getPathToOutput() {
        return Paths.get("cheese");
      }
    };
    resolver.addToIndex(rule);

    BuildTargetSourcePath path = new BuildTargetSourcePath(rule.getBuildTarget());

    Path resolved = pathResolver.getPath(path);

    assertEquals(Paths.get("cheese"), resolved);
  }

  @Test
  public void shouldReturnTheBuildTarget() {
    BuildTarget target = BuildTargetFactory.newInstance("//foo/bar:baz");
    BuildTargetSourcePath path = new BuildTargetSourcePath(target);

    assertEquals(target, path.getTarget());
  }

  @Test
  public void explicitlySetPath() {
    SourcePathResolver pathResolver = new SourcePathResolver(new BuildRuleResolver());
    BuildTarget target = BuildTargetFactory.newInstance("//foo/bar:baz");
    FakeBuildRule rule = new FakeBuildRule(target, pathResolver);
    Path path = Paths.get("blah");
    BuildTargetSourcePath buildTargetSourcePath = new BuildTargetSourcePath(
        rule.getBuildTarget(),
        path);
    assertEquals(target, buildTargetSourcePath.getTarget());
    assertEquals(path, pathResolver.getPath(buildTargetSourcePath));
  }

  @Test
  public void sameBuildTargetsWithDifferentPathsAreDifferent() {
    SourcePathResolver pathResolver = new SourcePathResolver(new BuildRuleResolver());
    BuildTarget target = BuildTargetFactory.newInstance("//foo/bar:baz");
    FakeBuildRule rule = new FakeBuildRule(target, pathResolver);
    BuildTargetSourcePath path1 =
        new BuildTargetSourcePath(
            rule.getBuildTarget(),
            Paths.get("something"));
    BuildTargetSourcePath path2 =
        new BuildTargetSourcePath(
            rule.getBuildTarget(),
            Paths.get("something else"));
    assertNotEquals(path1, path2);
    assertNotEquals(path1.hashCode(), path2.hashCode());
  }

}
