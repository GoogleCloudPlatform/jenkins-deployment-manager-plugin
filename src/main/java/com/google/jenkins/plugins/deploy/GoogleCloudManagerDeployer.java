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
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;

import java.io.IOException;

/**
 * After a successful build, this plugin deploys to Google Cloud Platform via the Deployment Manager
 * API.
 */
public class GoogleCloudManagerDeployer extends Recorder {
  /**
   * The type of deployment the user wants to deploy
   */
  private final AbstractCloudDeployment deployment;

  /**
   * Constructs a deployer.
   *
   * @param deployment The deployment to publish
   */
  @DataBoundConstructor
  public GoogleCloudManagerDeployer(AbstractCloudDeployment deployment) {
    this.deployment = requireNonNull(deployment);
  }

  @Exported
  public AbstractCloudDeployment getDeployment() {
    return deployment;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener)
      throws IOException, InterruptedException {
    // Deploys only on success.
    if (build.getResult() != Result.SUCCESS) {
      return true;
    }

    EnvVars environment;
    try {
      environment = build.getEnvironment(listener);
    } catch (IOException e) {
      e.printStackTrace(listener.error(Messages.GoogleCloudManagerDeployer_EnvironmentException()));
      build.setResult(Result.FAILURE);
      return false;
    }

    try {
      FilePath workspace = requireNonNull(build.getWorkspace());

      synchronized (deployment) {
        deployment.insert(workspace, environment, listener.getLogger());
      }
    } catch (CloudManagementException e) {
      e.printStackTrace(listener.error(e.getMessage()));
      build.setResult(Result.FAILURE);
      return false;
    }

    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public BuildStepMonitor getRequiredMonitorService() {
    return BuildStepMonitor.NONE;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DescriptorImpl getDescriptor() {
    return (DescriptorImpl) super.getDescriptor();
  }

  /**
   * <p>
   * The descriptor for our {@code GoogleCloudManagerDeployer} Jenkins plugin.
   * </p>
   * <p>
   * NOTE: This is given an ordinal such that it defaults to running after cloud storage, since a
   * common pattern is to upload an artifact prior to deploying it, and having to drag it into
   * position gets old fast.
   * </p>
   */
  @Extension(ordinal = -1.0)
  public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isApplicable(Class<? extends AbstractProject> aClass) {
      // Indicates that this builder can be used with all kinds of project types
      return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
      return Messages.GoogleCloudManagerDeployer_DisplayName();
    }
  }

  /**
   * {@link BuildStepDetailsProvider} for the Cloud Manager Deployer.
   */
  @Extension
  @DetailFor(value = GoogleCloudManagerDeployer.class)
  public static class DetailsProvider extends BuildStepDetailsProvider<GoogleCloudManagerDeployer> {
    /**
     * {@inheritDoc}
     */
    @Override
    public String getDetails(GoogleCloudManagerDeployer deployer) {
      return deployer.getDeployment().getDescriptor().getDisplayName();
    }
  }
}
