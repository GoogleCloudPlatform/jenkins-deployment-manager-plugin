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

import static java.util.Objects.requireNonNull;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.services.deploymentmanager.DeploymentManager;
import com.google.common.annotations.VisibleForTesting;
import com.google.jenkins.plugins.credentials.domains.DomainRequirementProvider;
import com.google.jenkins.plugins.credentials.oauth.GoogleOAuth2ScopeRequirement;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import com.google.jenkins.plugins.util.Executor;

import hudson.model.Hudson;

import java.security.GeneralSecurityException;

/**
 * This module abstracts how the cloud deployment implementations instantiate their connection to
 * the {@link DeploymentManager} service.
 */
public class CloudDeploymentModule {
  private Class<? extends AbstractCloudDeployment> clazz;

  /**
   * Constructs the module for instantiating dependencies of the cloud deployment implementation
   * with type {@code clazz}.
   *
   * @param clazz The type for which we instantiate dependencies.
   */
  public <T extends AbstractCloudDeployment> CloudDeploymentModule(Class<T> clazz) {
    this.clazz = requireNonNull(clazz);
  }

  /**
   * Interface for requesting new connections to the {@link DeploymentManager} service.
   *
   * @param credentials The credentials to use to authenticate with the service
   * @return a new instance of the {@link DeploymentManager} service for issuing requests
   * @throws CloudManagementException if a service connection cannot be established.
   */
  public DeploymentManager newManager(GoogleRobotCredentials credentials)
      throws CloudManagementException {
    try {
      DeploymentManager.Builder builder = new DeploymentManager.Builder(new NetHttpTransport(),
          new JacksonFactory(), requireNonNull(credentials).getGoogleCredential(getRequirement()));

      // The descriptor surfaces global overrides for the API being used
      // for cloud management.
      AbstractCloudDeploymentDescriptor descriptor = getDescriptor();

      return builder.setApplicationName(Messages.CloudDeploymentModule_AppName())
          .setRootUrl(descriptor.getRootUrl()).setServicePath(descriptor.getServicePath()).build();
    } catch (GeneralSecurityException e) {
      throw new CloudManagementException(Messages.CloudDeploymentModule_ConnectionError(), e);
    }
  }

  public GoogleOAuth2ScopeRequirement getRequirement() {
    return DomainRequirementProvider.of(clazz, GoogleOAuth2ScopeRequirement.class);
  }

  /**
   * Interface for requesting the {@link Executor} for executing requests.
   *
   * @return a new {@link Executor} instance for issuing requests
   */
  public Executor newExecutor() {
    return new Executor.Default();
  }

  /**
   * Retrieves the descriptor for the cloud deployment class this module covers.
   */
  @VisibleForTesting
  AbstractCloudDeploymentDescriptor getDescriptor() {
    return (AbstractCloudDeploymentDescriptor) Hudson.getInstance().getDescriptor(clazz);
  }
}
