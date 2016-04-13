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

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.FINE;

import com.google.api.client.googleapis.services.json.AbstractGoogleJsonClientRequest;
import com.google.api.services.deploymentmanager.DeploymentManager;
import com.google.api.services.deploymentmanager.model.ConfigFile;
import com.google.api.services.deploymentmanager.model.Deployment;
import com.google.api.services.deploymentmanager.model.ImportFile;
import com.google.api.services.deploymentmanager.model.Operation;
import com.google.api.services.deploymentmanager.model.TargetConfiguration;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import com.google.jenkins.plugins.util.ExecutorException;
import com.google.jenkins.plugins.util.NotFoundException;

import hudson.DescriptorExtensionList;
import hudson.EnvVars;
import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Describable;
import hudson.model.Descriptor;

import jenkins.model.Jenkins;

import java.io.IOException;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nullable;

/**
 * Instances of this class represent a deployment being managed by the Google Cloud Deployment
 * Manager.
 */
public abstract class AbstractCloudDeployment
    implements Describable<AbstractCloudDeployment>, ExtensionPoint {
  /**
   * Controls how verbose to make the logs
   */
  protected static final boolean verbose = false;
  /**
   * Number of checks before timing out.
   */
  @VisibleForTesting
  static final int MAX_CHECKS_UNTIL_TIMEOUT = 100;
  private static final Logger theLogger = Logger.getLogger(AbstractCloudDeployment.class.getName());
  /**
   * The credentials to use to authenticate with the cloud manager.
   */
  private final String credentialsId;
  /**
   * The deployment name, which may include unresolved build variables.
   */
  private final String deploymentName;
  /**
   * The module for providing our dependencies' instances.
   */
  private final CloudDeploymentModule module;
  /**
   * The outlet through which to log messages during a synchronized top-level operation.
   * <p/>
   * TODO: Remove this field to avoid synchronized for some operations.
   */
  private transient PrintStream logger;

  /**
   * Constructs the AbstractCloudDeployment from the provided information.
   *
   * @param credentialsId The credentials to use for service interactions.
   * @param deploymentName The unresolved deployment name to use
   * @param module The module to use for allocating dependencies
   */
  protected AbstractCloudDeployment(String credentialsId, String deploymentName,
      @Nullable CloudDeploymentModule module) {
    this.credentialsId = requireNonNull(credentialsId);
    this.deploymentName = requireNonNull(deploymentName);
    this.module = (module != null) ? module : getDescriptor().getModule();
  }

  /**
   * Boilerplate, see: https://wiki.jenkins-ci.org/display/JENKINS/Defining+a+new+extension+point
   */
  public static DescriptorExtensionList<AbstractCloudDeployment, AbstractCloudDeploymentDescriptor> all() {
    return Jenkins.getInstance().getDescriptorList(AbstractCloudDeployment.class);
  }

  /**
   * This method is called by plugins utilizing this extension point for managing deployments, such
   * as a {@code Publisher}, {@code BuildWrapper}, or {@code Cloud}.
   *
   * @param descriptor The descriptor of the plugin wishing to consume a cloud deployment
   * @return a list of applicable cloud deployment extensions
   */
  public static List<AbstractCloudDeploymentDescriptor> getCompatibleCloudDeployments(
      Descriptor descriptor) {
    LinkedList<AbstractCloudDeploymentDescriptor> cloudDeployments =
        new LinkedList<AbstractCloudDeploymentDescriptor>();

    for (AbstractCloudDeploymentDescriptor cloudDeployment : all()) {
      if (!cloudDeployment.isApplicable(descriptor)) {
        continue;
      }
      cloudDeployments.add(cloudDeployment);
    }

    return cloudDeployments;
  }

  public String getCredentialsId() {
    return credentialsId;
  }

  public String getDeploymentName() {
    return deploymentName;
  }

  protected CloudDeploymentModule getModule() {
    return module;
  }

  abstract public void insert(FilePath workspace, EnvVars environment, @Nullable PrintStream logger)
      throws CloudManagementException;

  /**
   * NOTE: This should be treated as final, but is not to allow mocking.
   * <p/>
   * Creates the given deployment.
   *
   * @param configFileContents The contents of the deployment config file
   * @param importPaths The paths of the deployment import files
   * @param environment The environment under which to execute the insertion
   * @param logger The logger through which to surface messaging
   * @throws CloudManagementException if the operation fails or times out
   */
  public /* final */ synchronized void insert(String configFileContents,
      Iterable<FilePath> importPaths, EnvVars environment, @Nullable PrintStream logger)
      throws CloudManagementException {
    try {
      // Set up the logging facilities
      this.logger = logger;
      ResolvedPath path = new ResolvedPath(getDeploymentName(), environment);
      theLogger.log(FINE, Messages.AbstractCloudDeployment_LogInsert(path));

      Deployment deployment = null;
      try {
        deployment = createDeploymentSpec(path.deploymentName, configFileContents, importPaths);
      } catch (CloudManagementException e) {
        log(Messages.AbstractCloudDeployment_InvalidDeploySpec());
        e.printStackTrace();
        throw e;
      }

      // Initiate the connection with a fresh access token,
      // shared across the requests.
      DeploymentManager manager = module.newManager(getCredentials());

      try {
        Operation operation = createDeployment(manager, deployment);

        // Block until the deployment is deployed
        waitUntilDeployed(manager, operation);
      } catch (CloudManagementException e) {
        log(Messages.AbstractCloudDeployment_DeployFailed(deploymentName));
        e.printStackTrace();
        log(Messages.AbstractCloudDeployment_InsertRollback());

        // Roll back the failed deployment
        delete(environment, logger);

        this.logger = logger; // Delete resets this
        log(Messages.AbstractCloudDeployment_RollbackSuccess());

        throw e;
      }

      log(Messages.AbstractCloudDeployment_DeployComplete());
    } finally {
      this.logger = null;
    }
  }

  private Deployment createDeploymentSpec(String deploymentName, String configFileContents,
      Iterable<FilePath> importPaths) throws CloudManagementException {
    ConfigFile configFile = new ConfigFile();
    configFile.setContent(configFileContents);

    List<ImportFile> importFiles = newArrayList();
    for (FilePath path : importPaths) {
      try {
        importFiles.add(new ImportFile().setName(path.getName()).setContent(path.readToString()));
      } catch (IOException e) {
        throw new CloudManagementException(e.getMessage(), e);
      } catch (InterruptedException e) {
        throw new CloudManagementException(e.getMessage(), e);
      }
    }

    TargetConfiguration targetConfig = new TargetConfiguration();
    targetConfig.setConfig(configFile);
    targetConfig.setImports(importFiles);

    Deployment deployment = new Deployment();
    deployment.setName(deploymentName);
    deployment.setTarget(targetConfig);
    return deployment;
  }

  /**
   * Retrieve the credentials for this cloud deployment.
   */
  protected GoogleRobotCredentials getCredentials() {
    return GoogleRobotCredentials.getById(getCredentialsId());
  }

  /**
   * Retrieve the project id for this cloud deployment.
   */
  protected String getProjectId() {
    return getCredentials().getProjectId();
  }

  /**
   * Pass the given request through the executor.
   */
  protected <T> T execute(AbstractGoogleJsonClientRequest<T> request)
      throws IOException, ExecutorException {
    return module.newExecutor().execute(request);
  }

  /**
   * Boilerplate, see: https://wiki.jenkins-ci.org/display/JENKINS/Defining+a+new+extension+point
   */
  public AbstractCloudDeploymentDescriptor getDescriptor() {
    return (AbstractCloudDeploymentDescriptor) Jenkins.getInstance().getDescriptor(getClass());
  }

  /**
   * NOTE: This should be treated as final, but is not to allow mocking.
   * <p/>
   * Deletes user's deployment.
   *
   * @param environment The environment under which to execute the deletion
   * @param logger The logger through which to surface messaging
   * @throws CloudManagementException if the operation fails or times out
   */
  public /* final */ synchronized void delete(EnvVars environment, @Nullable PrintStream logger)
      throws CloudManagementException {
    try {
      // Set up the logging facilities
      this.logger = logger;
      ResolvedPath path = new ResolvedPath(getDeploymentName(), environment);
      theLogger.log(FINE, Messages.AbstractCloudDeployment_LogDelete(path));

      // Initiate the connection with a fresh access token,
      // shared across the requests.
      DeploymentManager manager = module.newManager(getCredentials());

      // In the reverse order from insertion:
      // 1) Delete the deployment
      Operation operation = deleteDeployment(manager, path.deploymentName);

      // 2) Wait for the deployment to turndown
      waitUntilDeleted(manager, operation);

      log(Messages.AbstractCloudDeployment_DeleteComplete());
      // NOTE: The above argument threading is largely cosmetic, since all that
      // the JSON objects are used for are their names, which we already have;
      // however, it works nicely to convey the order of events.
    } finally {
      this.logger = null;
    }
  }

  /**
   * Creates a new deployment.
   *
   * @param manager The connection the the Deployment Manager service
   * @param deployment The deployment configuration information
   * @return The new deployment of the service we've inserted.
   * @throws CloudManagementException if anything goes wrong.
   */
  protected Operation createDeployment(DeploymentManager manager, Deployment deployment)
      throws CloudManagementException {
    try {
      Operation operation = execute(manager.deployments().insert(getProjectId(), deployment));

      if (!verbose) {
        log(Messages.AbstractCloudDeployment_CreatedDeploy(deployment.getName()));
      } else {
        log(Messages.AbstractCloudDeployment_CreatedDeployVerbose(deployment.getName(),
            deployment.toPrettyString()));
      }
      return operation;
    } catch (ExecutorException e) {
      throw new CloudManagementException(Messages.AbstractCloudDeployment_CreateDeployException(),
          e);
    } catch (IOException e) {
      throw new CloudManagementException(Messages.AbstractCloudDeployment_CreateDeployException(),
          e);
    }
  }

  /**
   * Fetches a deployment.
   *
   * @param manager The connection the the Cloud Manager service
   * @param userDeploymentName The resolved name of the user's deployment
   * @return The current state of our deployment of the service/template
   * @throws CloudManagementException if anything goes wrong.
   */
  protected Deployment getDeployment(DeploymentManager manager, String userDeploymentName)
      throws CloudManagementException, ExecutorException {
    try {
      return execute(manager.deployments().get(getProjectId(), userDeploymentName));
    } catch (NotFoundException e) {
      throw e; // Do not wrap these.
    } catch (IOException e) {
      throw new CloudManagementException(Messages.AbstractCloudDeployment_GetDeployException(), e);
    }
  }

  /**
   * Deletes a deployment.
   *
   * @param manager The connection the the Deployment Manager service
   * @param userDeploymentName The resolved name of the user's deployment
   * @return the created delete operation object.
   * @throws CloudManagementException if anything goes wrong.
   */
  protected Operation deleteDeployment(DeploymentManager manager, String userDeploymentName)
      throws CloudManagementException {
    try {
      Operation operation =
          execute(manager.deployments().delete(getProjectId(), userDeploymentName));
      if (operation == null) {
        throw new CloudManagementException(
            Messages.AbstractCloudDeployment_DeleteDeployException());
      }
      log(Messages.AbstractCloudDeployment_DeletedDeploy(userDeploymentName));
      return operation;
    } catch (NotFoundException e) {
      // If the above succeeds, but errors a retry could throw a
      // NotFoundException, which we should accept as success.
      return new Operation().setStatus(OperationStatus.DONE.name());
    } catch (ExecutorException e) {
      throw new CloudManagementException(Messages.AbstractCloudDeployment_DeleteDeployException(),
          e);
    } catch (IOException e) {
      throw new CloudManagementException(Messages.AbstractCloudDeployment_DeleteDeployException(),
          e);
    }
  }

  /**
   * Waits for the deployment insertion to terminate. If the deployment fails, or we time out
   * waiting a {@link CloudManagementException} is thrown.
   *
   * @param manager The connection the the Deployment Manager service.
   * @param operation The operation to wait for.
   * @throws CloudManagementException if anything goes wrong, the deployment fails, or we time out
   *         waiting.
   */
  protected void waitUntilDeployed(DeploymentManager manager, Operation operation)
      throws CloudManagementException {
    try {
      waitUntilDone(manager, operation, true);
    } catch (ExecutorException e) {
      throw new CloudManagementException(
          Messages.AbstractCloudDeployment_ExecutorExceptionWhileDeploying(), e);
    } catch (IOException e) {
      throw new CloudManagementException(
          Messages.AbstractCloudDeployment_IOExceptionWhileDeploying(), e);
    }
  }

  /**
   * Waits for the newly delete deployment operation to be done. If the deletion fails, or we time
   * out waiting a {@link CloudManagementException} is thrown.
   *
   * @param manager The connection the the Cloud Manager service
   * @param operation The delete operation to wait for.
   * @throws CloudManagementException if anything goes wrong, the deployment fails, or we time out
   *         waiting.
   */
  protected void waitUntilDeleted(DeploymentManager manager, Operation operation)
      throws CloudManagementException {
    try {
      waitUntilDone(manager, operation, false);
    } catch (ExecutorException e) {
      throw new CloudManagementException(
          Messages.AbstractCloudDeployment_ExecutorExceptionWhileDeploying(), e);
    } catch (IOException e) {
      throw new CloudManagementException(
          Messages.AbstractCloudDeployment_IOExceptionWhileDeploying(), e);
    }
  }

  /**
   * Waits for the given operation to terminate. If the deployment fails, or we time-out waiting a
   * {@link CloudManagementException} is thrown.
   *
   * @param manager The connection the the Deployment Manager service.
   * @param operation The operation to wait for.
   * @param areDeploying whether we are currently deploying or deleting
   * @throws CloudManagementException if anything goes wrong, the deployment fails, or we time out
   *         waiting.
   */
  protected void waitUntilDone(DeploymentManager manager, Operation operation, boolean areDeploying)
      throws CloudManagementException, ExecutorException, IOException {
    int checks = MAX_CHECKS_UNTIL_TIMEOUT;
    while (!OperationStatus.DONE.isStatusOf(operation)) {
      log(areDeploying ? Messages.AbstractCloudDeployment_WaitDeploy()
          : Messages.AbstractCloudDeployment_WaitDelete());
      module.newExecutor().sleep();
      if (checks <= 0) {
        throw new CloudManagementException(
            areDeploying ? Messages.AbstractCloudDeployment_WaitTimeoutDeploy()
                : Messages.AbstractCloudDeployment_WaitTimeoutDelete());
      }
      operation = execute(manager.operations().get(getProjectId(), operation.getName()));
      --checks;
    }
    checkOperationErrors(operation);
  }

  private void checkOperationErrors(Operation operation) throws CloudManagementException {
    if (operation.getError() != null) {
      List<Operation.Error.Errors> errors = operation.getError().getErrors();
      String errorMessages = Joiner.on("\n")
          .join(Iterables.transform(errors, new Function<Operation.Error.Errors, Object>() {
            @Override
            public Object apply(Operation.Error.Errors error) {
              return error.toString();
            }
          }));
      throw new CloudManagementException(errorMessages);
    }
  }

  /**
   * This is a short-hand that issues the given message through our client's logging stream.
   *
   * @param message The message to be logged.
   */
  protected void log(String message) {
    if (this.logger != null) {
      logger.println(message);
    }
  }

  /**
   * Helper type for managing the resolution of the deployment path into its final form and managing
   * the pieces.
   */
  protected static class ResolvedPath {
    public final String deploymentName;

    public ResolvedPath(String deploymentName, EnvVars environment) {
      this.deploymentName = Util.replaceMacro(deploymentName, environment);
    }

    public String toString() {
      return deploymentName;
    }
  }
}
