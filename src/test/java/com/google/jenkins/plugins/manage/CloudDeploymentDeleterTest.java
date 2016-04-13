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

import static com.google.jenkins.plugins.TestFixture.DEPLOYMENT_NAME;
import static com.google.jenkins.plugins.TestFixture.PROJECT_ID;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.google.api.services.deploymentmanager.DeploymentManager;
import com.google.jenkins.plugins.PathUtils;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import com.google.jenkins.plugins.deploy.GoogleCloudManagerBuildWrapper;
import com.google.jenkins.plugins.deploy.GoogleCloudManagerDeployer;
import com.google.jenkins.plugins.util.Executor;
import com.google.jenkins.plugins.util.MockExecutor;
import com.google.jenkins.plugins.util.NotFoundException;

import com.cloudbees.plugins.credentials.SystemCredentialsProvider;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.AbstractProject;

import jenkins.model.Jenkins;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.Verifier;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link CloudDeploymentDeleter}.
 */
public class CloudDeploymentDeleterTest {
  private static final String ROBOT_ID = "c3p0";

  @Rule
  public JenkinsRule jenkins = new JenkinsRule();

  @Rule
  public TemporaryFolder dir = new TemporaryFolder();

  private FilePath workspace;

  private EnvVars environment;

  @Mock
  private GoogleRobotCredentials credentials;

  private NotFoundException notFoundException;

  @Mock
  private com.google.api.client.http.HttpHeaders headers;

  private MockExecutor executor = new MockExecutor();
  @Rule
  public Verifier verifySawAll = new Verifier() {
    @Override
    public void verify() {
      assertTrue(executor.sawAll());
      assertFalse(executor.sawUnexpected());
    }
  };
  @Mock
  private AbstractProject project;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    this.workspace = new FilePath(PathUtils.makeTempDir(dir, "the", "workspace"));

    // TODO: Add some values for testing
    this.environment = new EnvVars();

    when(credentials.getId()).thenReturn(ROBOT_ID);
    when(credentials.getProjectId()).thenReturn(PROJECT_ID);
    // Allow for @WithoutJenkins
    if (jenkins.jenkins != null) {
      SystemCredentialsProvider.getInstance().getCredentials().add(credentials);
    }

    notFoundException = new NotFoundException();
  }

  @Test
  public void testBasicDelete() throws Exception {
    // The set of expected requests and responses.
    executor.throwWhen(DeploymentManager.Deployments.Delete.class, notFoundException);

    MockCloudDeploymentModule module = new MockCloudDeploymentModule(executor);
    AbstractCloudDeployment manager =
        new CloudDeploymentDeleter(credentials.getId(), DEPLOYMENT_NAME, module);

    manager.delete(environment, System.out);
  }

  ;

  @Test(expected = NotFoundException.class)
  public void testDeleteThenGet() throws Exception {
    // The set of expected requests and responses.
    executor.throwWhen(DeploymentManager.Deployments.Delete.class, notFoundException);
    executor.throwWhen(DeploymentManager.Deployments.Get.class, notFoundException);

    MockCloudDeploymentModule module = new MockCloudDeploymentModule(executor);
    AbstractCloudDeployment manager =
        new CloudDeploymentDeleter(credentials.getId(), DEPLOYMENT_NAME, module);

    manager.delete(environment, System.out);
    manager.getDeployment(module.newManager(credentials), DEPLOYMENT_NAME);
  }

  @Test
  public void testInsertAsDelete() throws Exception {
    // The set of expected requests and responses.
    executor.throwWhen(DeploymentManager.Deployments.Delete.class, notFoundException);

    AbstractCloudDeployment manager = new CloudDeploymentDeleter(credentials.getId(),
        DEPLOYMENT_NAME, new MockCloudDeploymentModule(executor));

    manager.insert(workspace, environment, System.out);
  }

  @Test
  public void testAcceptableContexts() throws Exception {
    AbstractCloudDeployment manager = new CloudDeploymentDeleter(credentials.getId(),
        DEPLOYMENT_NAME, new MockCloudDeploymentModule(executor));

    // Allowed in single-action contexts
    assertTrue(manager.getDescriptor()
        .isApplicable(Jenkins.getInstance().getDescriptorOrDie(GoogleCloudManagerDeployer.class)));

    // Disallowed in an insert/delete context
    assertFalse(manager.getDescriptor().isApplicable(
        Jenkins.getInstance().getDescriptorOrDie(GoogleCloudManagerBuildWrapper.class)));
  }

  private static class MockCloudDeploymentModule extends CloudDeploymentModule {
    private final Executor executor;

    public MockCloudDeploymentModule(Executor executor) {
      super(TemplatedCloudDeployment.class);
      this.executor = executor;
    }

    @Override
    public Executor newExecutor() {
      return executor;
    }
  }

}
