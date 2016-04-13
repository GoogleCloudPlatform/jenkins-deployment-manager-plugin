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

import com.google.api.services.deploymentmanager.DeploymentManagerScopes;
import com.google.common.base.Function;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.jenkins.plugins.credentials.domains.RequiresDomain;
import com.google.jenkins.plugins.credentials.oauth.GoogleOAuth2ScopeRequirement;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.util.FormValidation;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;

import javax.annotation.Nullable;

/**
 * This implementation of the cloud deployment inserts and/or deletes a set of resources specified
 * in a yaml-file template.
 */
@RequiresDomain(value = TemplatedCloudDeployment.ScopeRequirement.class)
public class TemplatedCloudDeployment extends AbstractCloudDeployment {
  /**
   * The workspace-relative path to the config file, which may include unresolved build variables.
   */
  private final String configFilePath;

  /**
   * A comma-separated list of paths of files to import into the config file.
   */
  private final String importPaths;

  /**
   * Constructs the cloud deployment from the provided information.
   *
   * @param credentialsId The set of credentials to use for service auth
   * @param deploymentName The unresolved deployment name to use
   * @param configFilePath The workspace-relative path to the config file to use
   * @param importPaths The comma-separated list of paths to import
   * @param module The module to use for instantiating dependencies
   */
  @DataBoundConstructor
  public TemplatedCloudDeployment(String credentialsId, String deploymentName,
      String configFilePath, String importPaths, @Nullable CloudDeploymentModule module) {
    super(credentialsId, deploymentName, module);
    this.configFilePath = requireNonNull(configFilePath);
    this.importPaths = requireNonNull(importPaths);
  }

  /**
   * Converts a comma-separated list of strings into a list of strings.
   *
   * @param commaSeparated a comma-separated string
   * @return a list of strings corresponding to the input comma-separated string
   */
  static Iterable<String> commaSeparatedToList(String commaSeparated) {
    if (commaSeparated.matches("\\s*")) {
      return new ArrayList<String>();
    }
    return Splitter.onPattern("\\s*,\\s*").split(commaSeparated);
  }

  static Iterable<FilePath> getFilePaths(final Iterable<String> paths, final FilePath workspace,
      final EnvVars environment) {
    return Iterables.transform(paths, new Function<String, FilePath>() {
      @Override
      public FilePath apply(@Nullable String path) {
        return workspace.child(Util.replaceMacro(path, environment));
      }
    });
  }

  public String getConfigFilePath() {
    return configFilePath;
  }

  public String getImportPaths() {
    return importPaths;
  }

  public /* final */ synchronized void insert(FilePath workspace, EnvVars environment,
      @Nullable PrintStream logger) throws CloudManagementException {
    try {
      FilePath resolvedConfig = workspace.child(Util.replaceMacro(configFilePath, environment));
      insert(resolvedConfig.readToString(),
          getFilePaths(commaSeparatedToList(importPaths), workspace, environment), environment,
          logger);
    } catch (IOException e) {
      throw new CloudManagementException(e.getMessage(), e);
    } catch (InterruptedException e) {
      throw new CloudManagementException(e.getMessage(), e);
    }
  }

  /**
   * Express the required scopes for an arbitrary Google Cloud deployment via this cloud deployment.
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
      this(TemplatedCloudDeployment.class);
    }

    public DescriptorImpl(Class<? extends TemplatedCloudDeployment> clazz) {
      super(clazz);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
      return Messages.TemplatedCloudDeployment_DisplayName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isApplicable(Descriptor descriptor) {
      // This is really only applicable to things within a
      // workspace context, from which a template can be loaded,
      // which implies SCM...
      // TODO: Limit this to an appropriate set of things.
      return true;
    }

    /**
     * Form validation for the {@code configFilePath} field.
     */
    public FormValidation doCheckConfigFilePath(@QueryParameter String configFilePath) {
      if (!Strings.isNullOrEmpty(configFilePath)) {
        return FormValidation.ok();
      } else {
        return FormValidation.error(Messages.TemplatedCloudDeployment_UnspecifiedConfigFileError());
      }
    }
  }
}
