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

import static java.util.Objects.requireNonNull;

import com.google.jenkins.plugins.manage.AbstractCloudDeployment;
import com.google.jenkins.plugins.manage.CloudManagementException;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;

import java.io.IOException;


/**
 * This {@code BuildWrapper} implementation configures an ephemeral deployment that is scoped to the
 * active build.
 */
public class GoogleCloudManagerBuildWrapper extends BuildWrapper {
  private final AbstractCloudDeployment deployment;

  /**
   * Constructs an ephemeral deployer.
   *
   * @param deployment The deployment to publish for the scope of the build.
   */
  @DataBoundConstructor
  public GoogleCloudManagerBuildWrapper(AbstractCloudDeployment deployment) {
    this.deployment = requireNonNull(deployment);
  }

  /**
   * The type of cloud deployment the user wants to deploy for the scope of the build.
   */
  @Exported
  public AbstractCloudDeployment getDeployment() {
    return deployment;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public EphemeralDeployment setUp(AbstractBuild build, Launcher launcher, BuildListener listener)
      throws IOException, InterruptedException {
    EnvVars environment = build.getEnvironment(listener);
    FilePath workspace = requireNonNull(build.getWorkspace());

    try {
      synchronized (deployment) {
        deployment.insert(workspace, environment, listener.getLogger());
      }
    } catch (CloudManagementException e) {
      e.printStackTrace(listener.error(e.getMessage()));
      return null; // Build must be aborted
    }

    return new EphemeralDeployment(environment);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DescriptorImpl getDescriptor() {
    return (DescriptorImpl) super.getDescriptor();
  }

  /**
   * The descriptor for our {@code GoogleCloudManagerBuildWrapper} plugin.
   */
  @Extension
  public static final class DescriptorImpl extends BuildWrapperDescriptor {
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isApplicable(AbstractProject<?, ?> project) {
      // Indicates that this builder can be used with all kinds of project types
      return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
      return Messages.GoogleCloudManagerBuildWrapper_DisplayName();
    }
  }

  /**
   * This is returned so that the build can tear down the ephemeral deployment we've created. It is
   * also used to export deployment endpoint env variables.
   */
  private final class EphemeralDeployment extends Environment {
    private final EnvVars envVars;

    /**
     * Construct the instance with a snapshot of the environment within which it was created in case
     * values that were used to configure it at the start of the build change before the end.
     *
     * @param envVars The set of environment variables used to spin up the ephemeral deployment, so
     *        we can tear it down with the same.
     */
    public EphemeralDeployment(EnvVars envVars) {
      this.envVars = requireNonNull(envVars);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean tearDown(AbstractBuild build, BuildListener listener)
        throws IOException, InterruptedException {
      try {
        deployment.delete(envVars, listener.getLogger());
      } catch (CloudManagementException e) {
        e.printStackTrace(listener.error(e.getMessage()));
        return false; // Build must be aborted
      }

      return true;
    }
  }
}
