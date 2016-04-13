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
package com.google.jenkins.plugins.deploy;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableList;

import hudson.tasks.BuildStep;
import hudson.tasks.Maven;
import hudson.tasks.Shell;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;

/**
 * Unit test for {@link BuildStepDetailsProvider}.
 */
public class BuildStepDetailsProviderTest {
  @Rule
  public JenkinsRule jenkins = new JenkinsRule();

  @Test
  public void getDetails_Maven() {
    Maven maven = new Maven("targets", "");
    assertEquals(maven.getTargets(), BuildStepDetailsProvider.resolveDetails(maven));
    // Check that Maven doesn't use "Invoke top-level Maven targets",
    // but instead uses our custom message.
    assertThat(maven.getDescriptor().getDisplayName())
        .isNotEqualTo(BuildStepDetailsProvider.resolveName(maven));
    assertEquals(Messages.BuildStepDetailsProvider_MavenName(),
        BuildStepDetailsProvider.resolveName(maven));
    assertEquals(ImmutableList.<String>of("mvn", "targets"),
        BuildStepDetailsProvider.resolveFullCmd(maven));
  }

  @Test
  public void getDetails_Shell() {
    Shell shell = new Shell("ls -l");
    assertEquals(shell.getCommand(), BuildStepDetailsProvider.resolveDetails(shell));
    assertEquals(shell.getDescriptor().getDisplayName(),
        BuildStepDetailsProvider.resolveName(shell));
    assertEquals(ImmutableList.<String>of("ls", "-l"),
        BuildStepDetailsProvider.resolveFullCmd(shell));
  }

  @Test
  @WithoutJenkins
  public void getDetails_noJenkins() {
    assertThat(BuildStepDetailsProvider.all()).isEmpty();
    assertNull(BuildStepDetailsProvider.resolveDetails(mock(Maven.class)));
    assertNull(BuildStepDetailsProvider.resolveName(mock(BuildStep.class)));
  }
}
