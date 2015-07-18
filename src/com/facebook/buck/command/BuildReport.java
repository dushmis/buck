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

package com.facebook.buck.command;

import com.facebook.buck.rules.BuildResult;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleSuccessType;
import com.facebook.buck.util.Ansi;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.Maps;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@VisibleForTesting
public class BuildReport {

  @SuppressWarnings("PMD.LooseCoupling")
  private final LinkedHashMap<BuildRule, Optional<BuildResult>> ruleToResult;

  /**
   * @param ruleToResult Keys are build rules built during this invocation of Buck. Values reflect
   *     the success of each build rule, if it succeeded. ({@link Optional#absent()} represents a
   *     failed build rule.)
   */
  public BuildReport(@SuppressWarnings("PMD.LooseCoupling") LinkedHashMap<
      BuildRule, Optional<BuildResult>> ruleToResult) {
    this.ruleToResult = ruleToResult;
  }

  public String generateForConsole(Ansi ansi) {
    StringBuilder report = new StringBuilder();
    for (Map.Entry<BuildRule, Optional<BuildResult>> entry : ruleToResult.entrySet()) {
      BuildRule rule = entry.getKey();
      Optional<BuildRuleSuccessType> success = Optional.absent();
      Optional<BuildResult> result = entry.getValue();
      if (result.isPresent()) {
        success = Optional.fromNullable(result.get().getSuccess());
      }

      String successIndicator;
      String successType;
      Path outputFile;
      if (success.isPresent()) {
        successIndicator = ansi.asHighlightedSuccessText("OK  ");
        successType = success.get().name();
        outputFile = rule.getPathToOutput();
      } else {
        successIndicator = ansi.asHighlightedFailureText("FAIL");
        successType = null;
        outputFile = null;
      }

      report.append(String.format(
              "%s %s%s%s\n",
              successIndicator,
              rule.getBuildTarget(),
              successType != null ? " " + successType : "",
              outputFile != null ? " " + outputFile : ""));
    }

    return report.toString();
  }

  public String generateJsonBuildReport() throws IOException {
    LinkedHashMap<String, Object> results = Maps.newLinkedHashMap();
    boolean isOverallSuccess = true;
    for (Map.Entry<BuildRule, Optional<BuildResult>> entry : ruleToResult.entrySet()) {
      BuildRule rule = entry.getKey();
      Optional<BuildRuleSuccessType> success = Optional.absent();
      Optional<BuildResult> result = entry.getValue();
      if (result.isPresent()) {
        success = Optional.fromNullable(result.get().getSuccess());
      }
      Map<String, Object> value = Maps.newLinkedHashMap();

      boolean isSuccess = success.isPresent();
      value.put("success", isSuccess);
      if (!isSuccess) {
        isOverallSuccess = false;
      }

      if (isSuccess) {
        value.put("type", success.get().name());
        Path outputFile = rule.getPathToOutput();
        value.put("output", outputFile != null ? outputFile.toString() : null);
      }
      results.put(rule.getFullyQualifiedName(), value);
    }

    Map<String, Object> report = Maps.newLinkedHashMap();
    report.put("success", isOverallSuccess);
    report.put("results", results);
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    return objectMapper.writeValueAsString(report);
  }
}
