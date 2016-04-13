/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.jenkins.plugins;

import static com.google.common.collect.Lists.transform;
import static org.junit.Assert.assertTrue;

import com.google.api.client.repackaged.com.google.common.base.Joiner;
import com.google.common.base.Function;

import hudson.FilePath;

import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Provides utility methods for manipulating paths.
 */
public class PathUtils {
  public static String toRemotePaths(List<FilePath> importPaths) {
    return Joiner.on(',').join(transform(importPaths, new Function<FilePath, String>() {
      @Override
      public String apply(FilePath input) {
        return input.getRemote();
      }
    }));
  }

  public static File makeTempDir(TemporaryFolder parentDir, String prefix, String suffix)
      throws IOException {
    File temp = parentDir.newFolder(prefix + suffix);
    assertTrue(temp.delete());
    assertTrue(temp.mkdir());

    return temp;
  }
}
