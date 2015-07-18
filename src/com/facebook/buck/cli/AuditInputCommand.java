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

package com.facebook.buck.cli;

import com.facebook.buck.graph.AbstractBottomUpTraversal;
import com.facebook.buck.json.BuildFileParseException;
import com.facebook.buck.log.Logger;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetException;
import com.facebook.buck.parser.BuildTargetParser;
import com.facebook.buck.parser.BuildTargetPatternParser;
import com.facebook.buck.parser.ParserConfig;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.TargetNode;
import com.facebook.buck.util.HumanReadableException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

public class AuditInputCommand extends AbstractCommand {

  private static final Logger LOG = Logger.get(AuditInputCommand.class);

  @Option(name = "--json",
      usage = "Output in JSON format")
  private boolean generateJsonOutput;

  public boolean shouldGenerateJsonOutput() {
    return generateJsonOutput;
  }

  @Argument
  private List<String> arguments = Lists.newArrayList();

  public List<String> getArguments() {
    return arguments;
  }

  @VisibleForTesting
  void setArguments(List<String> arguments) {
    this.arguments = arguments;
  }

  public List<String> getArgumentsFormattedAsBuildTargets(BuckConfig buckConfig) {
    return getCommandLineBuildTargetNormalizer(buckConfig).normalizeAll(getArguments());
  }

  @Override
  public int runWithoutHelp(final CommandRunnerParams params)
      throws IOException, InterruptedException {
    // Create a TargetGraph that is composed of the transitive closure of all of the dependent
    // TargetNodes for the specified BuildTargets.
    final ImmutableSet<String> fullyQualifiedBuildTargets = ImmutableSet.copyOf(
        getArgumentsFormattedAsBuildTargets(params.getBuckConfig()));

    if (fullyQualifiedBuildTargets.isEmpty()) {
      params.getConsole().printBuildFailure("Please specify at least one build target.");
      return 1;
    }

    ImmutableSet<BuildTarget> targets = FluentIterable
        .from(getArgumentsFormattedAsBuildTargets(params.getBuckConfig()))
        .transform(new Function<String, BuildTarget>() {
                     @Override
                     public BuildTarget apply(String input) {
                       return BuildTargetParser.INSTANCE.parse(
                           input,
                           BuildTargetPatternParser.fullyQualified());
                     }
                   })
        .toSet();

    LOG.debug("Getting input for targets: %s", targets);

    TargetGraph graph;
    try {
      graph = params.getParser().buildTargetGraphForBuildTargets(
          targets,
          new ParserConfig(params.getBuckConfig()),
          params.getBuckEventBus(),
          params.getConsole(),
          params.getEnvironment(),
          getEnableProfiling());
    } catch (BuildTargetException | BuildFileParseException e) {
      params.getConsole().printBuildFailureWithoutStacktrace(e);
      return 1;
    }

    if (shouldGenerateJsonOutput()) {
      return printJsonInputs(params, graph);
    }
    return printInputs(params, graph);
  }

  @Override
  public boolean isReadOnly() {
    return true;
  }

  @VisibleForTesting
  int printJsonInputs(final CommandRunnerParams params, TargetGraph graph) throws IOException {
    final SortedMap<String, ImmutableSortedSet<Path>> targetToInputs =
        new TreeMap<>();

    new AbstractBottomUpTraversal<TargetNode<?>, Void>(graph) {

      @Override
      public void visit(TargetNode<?> node) {
        LOG.debug(
            "Looking at inputs for %s",
            node.getBuildTarget().getFullyQualifiedName());

        SortedSet<Path> targetInputs = new TreeSet<>();
        for (Path input : node.getInputs()) {
          LOG.debug("Walking input %s", input);
          try {
            if (!params.getRepository().getFilesystem().exists(input)) {
              throw new HumanReadableException(
                  "Target %s refers to non-existent input file: %s", node, input);
            }
            targetInputs.addAll(params.getRepository().getFilesystem().getFilesUnderPath(input));
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
        targetToInputs.put(
            node.getBuildTarget().getFullyQualifiedName(),
            ImmutableSortedSet.copyOf(targetInputs));
      }

      @Override
      public Void getResult() {
        return null;
      }

    }.traverse();

    params.getObjectMapper().writeValue(
        params.getConsole().getStdOut(),
        targetToInputs);

    return 0;
  }

  private int printInputs(final CommandRunnerParams params, TargetGraph graph) {
    // Traverse the TargetGraph and print out all of the inputs used to produce each TargetNode.
    // Keep track of the inputs that have been displayed to ensure that they are not displayed more
    // than once.
    new AbstractBottomUpTraversal<TargetNode<?>, Void>(graph) {

      final Set<Path> inputs = Sets.newHashSet();

      @Override
      public void visit(TargetNode<?> node) {
        for (Path input : node.getInputs()) {
          LOG.debug("Walking input %s", input);
          try {
            if (!params.getRepository().getFilesystem().exists(input)) {
              throw new HumanReadableException(
                  "Target %s refers to non-existent input file: %s",
                  node,
                  input);
            }
            ImmutableSortedSet<Path> nodeContents = ImmutableSortedSet.copyOf(
                params.getRepository().getFilesystem().getFilesUnderPath(input));
            for (Path path : nodeContents) {
              putInput(path);
            }
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      }

      private void putInput(Path input) {
        boolean isNewInput = inputs.add(input);
        if (isNewInput) {
          params.getConsole().getStdOut().println(input);
        }
      }

      @Override
      public Void getResult() {
        return null;
      }

    }.traverse();

    return 0;
  }

  @Override
  public String getShortDescription() {
    return "provides facilities to audit build targets' input files";
  }

}
