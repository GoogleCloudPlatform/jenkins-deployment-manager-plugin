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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.jenkins.plugins.util.Resolve;

import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.util.FormValidation;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 * Descriptor from which CloudDeployment extensions must derive their descriptor.
 */
public abstract class AbstractCloudDeploymentDescriptor
    extends Descriptor<AbstractCloudDeployment> {
  private static final String VALID_COMPONENT = "[-a-zA-Z0-9_]{1,64}";
  private static final Pattern COMPONENT_REGEX = Pattern.compile(VALID_COMPONENT);
  @Nullable
  private String rootUrl;
  @Nullable
  private String servicePath;
  private CloudDeploymentModule module;

  /**
   * Create the descriptor of the cloud deployment from it's type on associated module for
   * instantiating dependencies.
   */
  protected AbstractCloudDeploymentDescriptor(Class<? extends AbstractCloudDeployment> clazz,
      CloudDeploymentModule module) {
    super(requireNonNull(clazz));
    this.module = module;
    load();
  }

  /**
   * Boilerplate, see: https://wiki.jenkins-ci.org/display/JENKINS/Defining+a+new+extension+point
   */
  protected AbstractCloudDeploymentDescriptor(Class<? extends AbstractCloudDeployment> clazz) {
    this(requireNonNull(clazz), new CloudDeploymentModule(clazz));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
    json = json.getJSONObject(getDisplayName());
    rootUrl = json.getString("rootUrl");
    servicePath = json.getString("servicePath");
    save();
    return true;
  }

  /**
   * Retrieve the root URL that should be used for Google Deployment Manager API calls.
   */
  public String getRootUrl() {
    return Strings.isNullOrEmpty(rootUrl) ? "https://www.googleapis.com" : rootUrl;
  }

  /**
   * Retrieve the service path that should be used for Google Deployment Manager API calls.
   */
  public String getServicePath() {
    return Strings.isNullOrEmpty(servicePath) ? "deploymentmanager/v2/projects/" : servicePath;
  }

  /**
   * Determines whether the given cloud deployment is suitable for use with a particular class of
   * extensions.
   * <p/>
   * For instance, some may be designed for use with a {@code Cloud}, while others may be designed
   * for use with a {@code Publisher}.
   */
  public abstract boolean isApplicable(Descriptor descriptor);

  /**
   * Retrieve the module to use for instantiating dependencies for instances described by this
   * descriptor.
   */
  public CloudDeploymentModule getModule() {
    return module;
  }

  /**
   * Validate a sample resolution of the component name.
   */
  private void validateComponentName(String componentName) throws FormValidation {
    if (Strings.isNullOrEmpty(componentName)) {
      throw FormValidation.error(Messages.AbstractCloudDeploymentDescriptor_NotEmpty());
    }

    if (!COMPONENT_REGEX.matcher(componentName).matches()) {
      throw FormValidation
          .error(Messages.AbstractCloudDeploymentDescriptor_BadComponent(VALID_COMPONENT));
    }
  }

  /**
   * This callback validates the {@code configFilePath} specified by the user.
   */
  public FormValidation doCheckConfigFilePath(
      @QueryParameter("configFilePath") final String configFilePath,
      @AncestorInPath AbstractProject project) throws IOException {
    String resolvedInput = Resolve.resolveBuiltinWithCustom(configFilePath,
        ImmutableMap.of("JOB_NAME", project.getName()));
    try {
      if (Strings.isNullOrEmpty(resolvedInput)) {
        throw FormValidation.error(Messages.AbstractCloudDeploymentDescriptor_NotEmpty());
      }
      return FormValidation.ok();
    } catch (FormValidation issue) {
      return reportValidationIssue(resolvedInput, issue);
    }
  }

  /**
   * This callback validates the {@code deploymentName} specified by the user.
   */
  public FormValidation doCheckDeploymentName(
      @QueryParameter("deploymentName") final String deploymentName,
      @AncestorInPath AbstractProject project) throws IOException {
    String resolvedInput = Resolve.resolveBuiltinWithCustom(deploymentName,
        ImmutableMap.of("JOB_NAME", project.getName()));
    try {
      validateComponentName(resolvedInput);
      return FormValidation.ok();
    } catch (FormValidation issue) {
      return reportValidationIssue(resolvedInput, issue);
    }
  }

  private FormValidation reportValidationIssue(String resolvedInput, FormValidation issue) {
    switch (issue.kind) {
      case OK:
        return issue;
      case ERROR:
        return FormValidation.error(Messages
            .AbstractCloudDeploymentDescriptor_SampleResolution(issue.getMessage(), resolvedInput));
      case WARNING:
        return FormValidation.warning(Messages
            .AbstractCloudDeploymentDescriptor_SampleResolution(issue.getMessage(), resolvedInput));
      default:
        return issue;
    }
  }

}
