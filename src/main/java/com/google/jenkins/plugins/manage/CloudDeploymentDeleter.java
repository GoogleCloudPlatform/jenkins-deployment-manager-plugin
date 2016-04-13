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
package com.google.jenkins.plugins.manage;

import com.google.api.services.deploymentmanager.DeploymentManagerScopes;
import com.google.common.collect.ImmutableList;
import com.google.jenkins.plugins.credentials.domains.RequiresDomain;
import com.google.jenkins.plugins.credentials.oauth.GoogleOAuth2ScopeRequirement;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Descriptor;
import hudson.tasks.Publisher;

import org.kohsuke.stapler.DataBoundConstructor;

import java.io.PrintStream;
import java.util.Collection;

import javax.annotation.Nullable;

/**
 * A specialized cloud deployment that is solely intended to turn down other deployments, regardless
 * of their inserted form. This cloud deployment may only be used from within {@link Publisher}s,and
 * wires {@link AbstractCloudDeployment#insert} to {@link #delete} to enable
 * {@link AbstractCloudDeployment#insert} to act as the entry point for single-action cloud
 * deployments.
 */
@RequiresDomain(value = CloudDeploymentDeleter.ScopeRequirement.class)
public class CloudDeploymentDeleter extends AbstractCloudDeployment {
  /**
   * Constructs the cloud deployment from the provided information.
   *
   * @param credentialsId The set of credentials to use for service auth
   * @param deploymentName The unresolved deployment name to use
   */
  @DataBoundConstructor
  public CloudDeploymentDeleter(String credentialsId, String deploymentName,
      @Nullable CloudDeploymentModule module) {
    super(credentialsId, deploymentName, module);
  }

  /**
   * Wire insert to delete
   */
  @Override
  public void insert(FilePath workspace, EnvVars environment, @Nullable PrintStream logger)
      throws CloudManagementException {
    this.delete(environment, logger);
  }

  /**
   * Express the required scopes for deleting an arbitrary cloud deployment via this cloud
   * deployment.
   */
  @Extension
  public static class ScopeRequirement extends GoogleOAuth2ScopeRequirement {
    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<String> getScopes() {
      return ImmutableList.of(DeploymentManagerScopes.NDEV_CLOUDMAN);
    }
  }

  /**
   * Denotes that this is a cloud deployment plugin.
   */
  @Extension
  public static class DescriptorImpl extends AbstractCloudDeploymentDescriptor {
    public DescriptorImpl() {
      this(CloudDeploymentDeleter.class);
    }

    public DescriptorImpl(Class<? extends CloudDeploymentDeleter> clazz) {
      super(clazz);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
      return Messages.CloudDeploymentDeleter_DisplayName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isApplicable(Descriptor descriptor) {
      // Only allow this within the context of publishers
      return Publisher.class.isAssignableFrom(descriptor.clazz);
    }
  }
}
