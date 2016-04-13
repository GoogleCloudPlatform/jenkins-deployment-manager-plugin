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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.jenkins.plugins.metadata.scm.JenkinsUtils;

import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.tasks.BuildStep;
import hudson.tasks.Maven;
import hudson.tasks.Shell;

import java.util.Arrays;
import java.util.Collection;

import javax.annotation.Nullable;

/**
 * <p>
 * An extension to provide additional details for a given {@link BuildStep}. Subclasses will need to
 * overwrite {@link #getDetails(BuildStep)} for the build step type they supported.
 * </p>
 * <p>
 * To provide details for a new build step, add a class as
 * 
 * <pre>
 * <code>
 *   {@literal @}Extension
 *   {@literal @}DetailFor(value = AwesomeBuildStep.class)
 *   public static class AwesomeBuildStepDetailsProvider
 *       extends BuildStepDetailsProvider{@code <AwesomeBuildStep>} {
 *     {@literal @}Override
 *     public String getBuildStepDetails(AwesomeBuildStep myAwesomeBuildStep) {
 *       return myAwesomeBuildStep.getDetails(); // or other logic here
 *     }
 *   }
 * </code>
 * </pre>
 * 
 * See examples in {@link MavenBuildStepDetailsProvider} and {@link ShellBuildStepDetailsProvider}
 * below.
 * </p>
 *
 * @param <T> the supported build step type.
 */
public abstract class BuildStepDetailsProvider<T extends BuildStep> implements ExtensionPoint {
  /**
   * @param bs A given {@link BuildStep}.
   * @return the details obtained from the {@link BuildStep}, null if there is not an extension to
   *         obtain details.
   */
  @Nullable
  public static String resolveDetails(BuildStep bs) {
    for (BuildStepDetailsProvider<BuildStep> provider : all()) {
      if (provider.isApplicable(bs)) {
        return provider.getDetails(bs);
      }
    }
    return null;
  }

  /**
   * @param bs A given {@link BuildStep}.
   * @return the full tokenized command line including the program name.
   */
  @Nullable
  public static ImmutableList<String> resolveFullCmd(BuildStep bs) {
    for (BuildStepDetailsProvider<BuildStep> provider : all()) {
      if (provider.isApplicable(bs)) {
        String fullCmd = provider.getFullCmd(bs);
        // Some build steps are not triggered by command line command,
        // for example, GoogleCloudStorageUploader. For them, the fullCmd
        // with be null or empty.
        if (!Strings.isNullOrEmpty(fullCmd)) {
          return ImmutableList.copyOf(Arrays.asList(fullCmd.split("\\s+")));
        } else {
          return ImmutableList.<String>of();
        }
      }
    }
    return null;
  }

  /**
   * @param bs a {@link BuildStep}.
   * @return the display name of the {@link BuildStep}, if it is a {@link Describable}, otherwise
   *         null.
   */
  @Nullable
  public static String resolveName(BuildStep bs) {
    for (BuildStepDetailsProvider<BuildStep> provider : all()) {
      if (provider.isApplicable(bs)) {
        return provider.getName(bs);
      }
    }

    // Default to the display name of the plugin, if there isn't
    // a detail provider
    return defaultName(bs);
  }

  protected static String defaultName(BuildStep bs) {
    return bs instanceof Describable<?> ? ((Describable<?>) bs).getDescriptor().getDisplayName()
        : null;
  }

  /**
   * @return all the extension for this {@link ExtensionPoint} {@link BuildStepDetailsProvider}.
   */
  @SuppressWarnings("rawtypes")
  public static Collection<BuildStepDetailsProvider> all() {
    return JenkinsUtils.getExtensionList(BuildStepDetailsProvider.class);
  }

  /**
   * @param bs A given {@link BuildStep}.
   * @return the details of the build step.
   */
  public abstract String getDetails(T bs);

  /**
   * This provides a hook by which detail provider may provide different full command line details
   * as getDetails() method.
   *
   * @param bs A given {@link BuildStep}.
   * @return the full command line including the program name.
   */
  public String getFullCmd(T bs) {
    return getDetails(bs);
  }

  /**
   * This provides a hook by which detail providers may decide to not use the display name of the
   * plugin as the waypoint name.
   */
  public String getName(T bs) {
    return defaultName(bs);
  }

  /**
   * @param buildStep a given {@link BuildStep}.
   * @return whether the {@link BuildStep} is supported by this extension.
   */
  protected boolean isApplicable(BuildStep buildStep) {
    return isApplicable(buildStep.getClass());
  }

  private boolean isApplicable(Class<? extends BuildStep> buildStepClass) {
    // Checks whether this extension supports the given build step class.
    return getSupportedBuildStepClass().isAssignableFrom(buildStepClass);
  }

  private Class<? extends BuildStep> getSupportedBuildStepClass() {
    DetailFor detailFor = getClass().getAnnotation(DetailFor.class);
    requireNonNull(detailFor, "@DetailFor must be present on BuildStepDetailsProviders");
    requireNonNull(detailFor.value(), "@DetailFor must have a value()");

    return detailFor.value();
  }

  /**
   * {@link BuildStepDetailsProvider} for {@link Maven}.
   */
  @Extension
  @DetailFor(value = Maven.class)
  public static class MavenBuildStepDetailsProvider extends BuildStepDetailsProvider<Maven> {
    /**
     * {@inheritDoc}
     */
    @Override
    public String getDetails(Maven maven) {
      return maven.getTargets();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName(Maven maven) {
      return com.google.jenkins.plugins.deploy.Messages.BuildStepDetailsProvider_MavenName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getFullCmd(Maven maven) {
      return "mvn " + getDetails(maven);
    }
  }

  /**
   * {@link BuildStepDetailsProvider} for {@link Shell}.
   */
  @Extension
  @DetailFor(value = Shell.class)
  public static class ShellBuildStepDetailsProvider extends BuildStepDetailsProvider<Shell> {
    /**
     * {@inheritDoc}
     */
    @Override
    public String getDetails(Shell shell) {
      return shell.getCommand();
    }
  }
}
