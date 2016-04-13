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

import static com.google.api.client.http.HttpStatusCodes.STATUS_CODE_FORBIDDEN;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.jenkins.plugins.TestFixture.CONFIG_FILE;
import static com.google.jenkins.plugins.TestFixture.CONFIG_FILE_CONTENTS;
import static com.google.jenkins.plugins.TestFixture.DEPLOYMENT_NAME;
import static com.google.jenkins.plugins.TestFixture.EMPTY_IMPORT_PATH;
import static com.google.jenkins.plugins.TestFixture.IMPORT_PATHS;
import static com.google.jenkins.plugins.TestFixture.PROJECT_ID;
import static com.google.jenkins.plugins.TestFixture.TEMPLATE1_CONTENTS;
import static com.google.jenkins.plugins.TestFixture.TEMPLATE2_CONTENTS;
import static com.google.jenkins.plugins.deploy.OperationFactory.newDoneOperation;
import static com.google.jenkins.plugins.deploy.OperationFactory.newErrorOperation;
import static com.google.jenkins.plugins.deploy.OperationFactory.newRunningOperation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.google.api.client.http.HttpResponseException;
import com.google.api.services.deploymentmanager.DeploymentManager;
import com.google.common.collect.ImmutableList;
import com.google.jenkins.plugins.PathUtils;
import com.google.jenkins.plugins.TestFixture;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import com.google.jenkins.plugins.util.Executor;
import com.google.jenkins.plugins.util.ExecutorException;
import com.google.jenkins.plugins.util.MockExecutor;
import com.google.jenkins.plugins.util.NotFoundException;

import com.cloudbees.plugins.credentials.SystemCredentialsProvider;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.AbstractProject;
import hudson.util.FormValidation;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.Verifier;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.List;

/**
 * Tests for {@link TemplatedCloudDeployment}.
 */
public class TemplatedCloudDeploymentTest {
  private static final String STATUS_MESSAGE = "doesn't matter";

  private static final String ROBOT_ID = "c3p0";

  @Rule
  public JenkinsRule jenkins = new JenkinsRule();

  @Rule
  public TemporaryFolder dir = new TemporaryFolder();

  private FilePath workspace;

  private EnvVars environment;

  private FilePath configFilePath;

  @Mock
  private GoogleRobotCredentials credentials;

  private NotFoundException notFoundException;

  private ExecutorException executorException;

  private HttpResponseException forbiddenJsonException;

  @Mock
  private com.google.api.client.http.HttpHeaders headers;

  private List<FilePath> importPaths;

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
    this.configFilePath = workspace.child(CONFIG_FILE);
    this.importPaths = ImmutableList.of(workspace.child(TestFixture.IMPORT_PATH_1),
        workspace.child(TestFixture.IMPORT_PATH_2));

    // TODO: Add some values for testing
    this.environment = new EnvVars();

    when(credentials.getId()).thenReturn(ROBOT_ID);
    when(credentials.getProjectId()).thenReturn(PROJECT_ID);
    SystemCredentialsProvider.getInstance().getCredentials().add(credentials);

    notFoundException = new NotFoundException();

    executorException = new ExecutorException() {};

    forbiddenJsonException =
        new HttpResponseException.Builder(STATUS_CODE_FORBIDDEN, STATUS_MESSAGE, headers).build();
  }

  @Test
  public void insertEverythingTest() throws Exception {
    configFilePath.write(CONFIG_FILE_CONTENTS, "UTF-8");
    importPaths.get(0).write(TEMPLATE1_CONTENTS, "UTF-8");
    importPaths.get(1).write(TEMPLATE2_CONTENTS, "UTF-8");

    executor.when(DeploymentManager.Deployments.Insert.class, newDoneOperation());

    AbstractCloudDeployment manager = new TemplatedCloudDeployment(credentials.getId(),
        DEPLOYMENT_NAME, CONFIG_FILE, IMPORT_PATHS, new MockCloudDeploymentModule(executor));

    synchronized (manager) {
      manager.insert(workspace, environment, System.out);
    }
  }

  ;

  @Test(expected = CloudManagementException.class)
  public void deploymentInsertionExceptionThatInsertedTest() throws Exception {
    configFilePath.write(CONFIG_FILE_CONTENTS, "UTF-8");
    importPaths.get(0).write(TEMPLATE1_CONTENTS, "UTF-8");
    importPaths.get(1).write(TEMPLATE2_CONTENTS, "UTF-8");

    executor.throwWhen(DeploymentManager.Deployments.Insert.class, executorException);
    executor.when(DeploymentManager.Deployments.Delete.class, newDoneOperation());

    AbstractCloudDeployment manager = new TemplatedCloudDeployment(credentials.getId(),
        DEPLOYMENT_NAME, CONFIG_FILE, EMPTY_IMPORT_PATH, new MockCloudDeploymentModule(executor));

    synchronized (manager) {
      manager.insert(CONFIG_FILE_CONTENTS, ImmutableList.<FilePath>of(), environment, System.out);
    }
  }

  @Test
  public void deleteEverythingTwoWaitTest() throws Exception {
    configFilePath.write(CONFIG_FILE_CONTENTS, "UTF-8");
    importPaths.get(0).write(TEMPLATE1_CONTENTS, "UTF-8");
    importPaths.get(1).write(TEMPLATE2_CONTENTS, "UTF-8");

    // The set of expected requests and responses.
    executor.when(DeploymentManager.Deployments.Delete.class, newRunningOperation());
    executor.when(DeploymentManager.Operations.Get.class, newRunningOperation());
    executor.when(DeploymentManager.Operations.Get.class, newDoneOperation());

    AbstractCloudDeployment manager = new TemplatedCloudDeployment(credentials.getId(),
        DEPLOYMENT_NAME, CONFIG_FILE, IMPORT_PATHS, new MockCloudDeploymentModule(executor));

    manager.delete(environment, System.out);
  }

  @Test(expected = CloudManagementException.class)
  public void deleteEverythingWithNotFoundTest() throws Exception {
    configFilePath.write(CONFIG_FILE_CONTENTS, "UTF-8");
    importPaths.get(0).write(TEMPLATE1_CONTENTS, "UTF-8");
    importPaths.get(1).write(TEMPLATE2_CONTENTS, "UTF-8");

    // The set of expected requests and responses.
    executor.when(DeploymentManager.Deployments.Delete.class, newRunningOperation());
    executor.throwWhen(DeploymentManager.Operations.Get.class, notFoundException);

    AbstractCloudDeployment manager = new TemplatedCloudDeployment(credentials.getId(),
        DEPLOYMENT_NAME, CONFIG_FILE, IMPORT_PATHS, new MockCloudDeploymentModule(executor));

    manager.delete(environment, System.out);
  }

  @Test
  public void deleteEverythingNoWaitNotFoundTest() throws Exception {
    configFilePath.write(CONFIG_FILE_CONTENTS, "UTF-8");
    importPaths.get(0).write(TEMPLATE1_CONTENTS, "UTF-8");
    importPaths.get(1).write(TEMPLATE2_CONTENTS, "UTF-8");

    // The set of expected requests and responses.
    executor.throwWhen(DeploymentManager.Deployments.Delete.class, notFoundException);

    AbstractCloudDeployment manager = new TemplatedCloudDeployment(credentials.getId(),
        DEPLOYMENT_NAME, CONFIG_FILE, IMPORT_PATHS, new MockCloudDeploymentModule(executor));

    manager.delete(environment, System.out);
  }

  @Test(expected = CloudManagementException.class)
  public void deleteReturnsNullTest() throws Exception {
    configFilePath.write(CONFIG_FILE_CONTENTS, "UTF-8");
    importPaths.get(0).write(TEMPLATE1_CONTENTS, "UTF-8");
    importPaths.get(1).write(TEMPLATE2_CONTENTS, "UTF-8");

    // The set of expected requests and responses.
    executor.when(DeploymentManager.Deployments.Delete.class, null);

    AbstractCloudDeployment manager = new TemplatedCloudDeployment(credentials.getId(),
        DEPLOYMENT_NAME, CONFIG_FILE, IMPORT_PATHS, new MockCloudDeploymentModule(executor));

    manager.delete(environment, System.out);
  }

  @Test(expected = CloudManagementException.class)
  public void insertWithTimeoutTest() throws Exception {
    configFilePath.write(CONFIG_FILE_CONTENTS, "UTF-8");
    importPaths.get(0).write(TEMPLATE1_CONTENTS, "UTF-8");
    importPaths.get(1).write(TEMPLATE2_CONTENTS, "UTF-8");

    executor.when(DeploymentManager.Deployments.Insert.class, newRunningOperation());

    for (int i = 0; i < AbstractCloudDeployment.MAX_CHECKS_UNTIL_TIMEOUT; ++i) {
      executor.when(DeploymentManager.Operations.Get.class, newRunningOperation());
    }
    // Before dying, we must attempt to rollback the insertion to avoid
    // leaving around garbage.
    executor.when(DeploymentManager.Deployments.Delete.class, null);

    AbstractCloudDeployment manager = new TemplatedCloudDeployment(credentials.getId(),
        DEPLOYMENT_NAME, CONFIG_FILE, IMPORT_PATHS, new MockCloudDeploymentModule(executor));

    synchronized (manager) {
      manager.insert(workspace, environment, System.out);
    }
  }

  @Test(expected = CloudManagementException.class)
  public void insertWith403Test() throws Exception {
    configFilePath.write(CONFIG_FILE_CONTENTS, "UTF-8");
    importPaths.get(0).write(TEMPLATE1_CONTENTS, "UTF-8");
    importPaths.get(1).write(TEMPLATE2_CONTENTS, "UTF-8");

    executor.throwWhen(DeploymentManager.Deployments.Insert.class, forbiddenJsonException);
    executor.when(DeploymentManager.Deployments.Delete.class, newErrorOperation());
    // We should not see any further requests.

    AbstractCloudDeployment manager = new TemplatedCloudDeployment(credentials.getId(),
        DEPLOYMENT_NAME, CONFIG_FILE, IMPORT_PATHS, new MockCloudDeploymentModule(executor));

    // We expect this to die on a CloudManagementException because the
    // template already exists.
    synchronized (manager) {
      manager.insert(workspace, environment, System.out);
    }
  }

  @Test(expected = CloudManagementException.class)
  public void cannotDeleteDeploymentTest() throws Exception {
    configFilePath.write(CONFIG_FILE_CONTENTS, "UTF-8");
    importPaths.get(0).write(TEMPLATE1_CONTENTS, "UTF-8");
    importPaths.get(1).write(TEMPLATE2_CONTENTS, "UTF-8");

    // The set of expected requests and responses.
    executor.throwWhen(DeploymentManager.Deployments.Delete.class, new IOException("test"));

    AbstractCloudDeployment manager = new TemplatedCloudDeployment(credentials.getId(),
        DEPLOYMENT_NAME, CONFIG_FILE, IMPORT_PATHS, new MockCloudDeploymentModule(executor));

    manager.delete(environment, System.out);
  }

  @Test(expected = CloudManagementException.class)
  public void cannotInsertDeploymentTest() throws Exception {
    configFilePath.write(CONFIG_FILE_CONTENTS, "UTF-8");
    importPaths.get(0).write(TEMPLATE1_CONTENTS, "UTF-8");
    importPaths.get(1).write(TEMPLATE2_CONTENTS, "UTF-8");

    executor.throwWhen(DeploymentManager.Deployments.Insert.class, new IOException("test"));
    // Before dying, we must attempt to rollback the insertion to avoid
    // leaving around garbage.
    executor.when(DeploymentManager.Deployments.Delete.class, null);

    AbstractCloudDeployment manager = new TemplatedCloudDeployment(credentials.getId(),
        DEPLOYMENT_NAME, CONFIG_FILE, IMPORT_PATHS, new MockCloudDeploymentModule(executor));

    // We expect this to die on a CloudManagementException because the
    // deployment cannot be inserted.
    synchronized (manager) {
      manager.insert(workspace, environment, System.out);
    }
  }

  @Test(expected = CloudManagementException.class)
  public void cannotInsertDueToMissingImportFileTest() throws Exception {
    configFilePath.write(CONFIG_FILE_CONTENTS, "UTF-8");
    importPaths.get(0).write(TEMPLATE1_CONTENTS, "UTF-8");

    AbstractCloudDeployment manager = new TemplatedCloudDeployment(credentials.getId(),
        DEPLOYMENT_NAME, CONFIG_FILE, IMPORT_PATHS, new MockCloudDeploymentModule(executor));

    // We expect this to die on a CloudManagementException because the
    // deployment cannot be inserted.
    synchronized (manager) {
      manager.insert(workspace, environment, System.out);
    }
  }

  @Test(expected = CloudManagementException.class)
  public void cannotInsertOrRollbackDeploymentTest() throws Exception {
    configFilePath.write(CONFIG_FILE_CONTENTS, "UTF-8");
    importPaths.get(0).write(TEMPLATE1_CONTENTS, "UTF-8");
    importPaths.get(1).write(TEMPLATE2_CONTENTS, "UTF-8");

    executor.throwWhen(DeploymentManager.Deployments.Insert.class, new IOException("test"));
    // Before dying, we must attempt to rollback the insertion to avoid
    // leaving around garbage.
    executor.throwWhen(DeploymentManager.Deployments.Delete.class,
        new IOException("unable to rollback"));

    AbstractCloudDeployment manager = new TemplatedCloudDeployment(credentials.getId(),
        DEPLOYMENT_NAME, CONFIG_FILE, IMPORT_PATHS, new MockCloudDeploymentModule(executor));

    // We expect this to die on a CloudManagementException because the
    // deployment cannot be inserted.
    synchronized (manager) {
      manager.insert(workspace, environment, System.out);
    }
  }

  @Test(expected = CloudManagementException.class)
  public void insertDeploymentWithErrorOperationTest() throws Exception {
    configFilePath.write(CONFIG_FILE_CONTENTS, "UTF-8");
    importPaths.get(0).write(TEMPLATE1_CONTENTS, "UTF-8");
    importPaths.get(1).write(TEMPLATE2_CONTENTS, "UTF-8");

    executor.when(DeploymentManager.Deployments.Insert.class, newErrorOperation());
    executor.when(DeploymentManager.Deployments.Delete.class, newErrorOperation());

    AbstractCloudDeployment manager = new TemplatedCloudDeployment(credentials.getId(),
        DEPLOYMENT_NAME, CONFIG_FILE, IMPORT_PATHS, new MockCloudDeploymentModule(executor));

    // We expect this to die on a CloudManagementException because the
    // deployment cannot be inserted.
    synchronized (manager) {
      manager.insert(workspace, environment, System.out);
    }
  }

  @Test
  public void deleteNotFoundTest() throws Exception {
    configFilePath.write(CONFIG_FILE_CONTENTS, "UTF-8");
    importPaths.get(0).write(TEMPLATE1_CONTENTS, "UTF-8");
    importPaths.get(1).write(TEMPLATE2_CONTENTS, "UTF-8");

    executor.throwWhen(DeploymentManager.Deployments.Delete.class, notFoundException);

    AbstractCloudDeployment manager = new TemplatedCloudDeployment(credentials.getId(),
        DEPLOYMENT_NAME, CONFIG_FILE, IMPORT_PATHS, new MockCloudDeploymentModule(executor));

    // We expect this to die on a CloudManagementException because the
    // deployment's status cannot be retrieved.
    synchronized (manager) {
      manager.delete(environment, System.out);
    }
  }

  @Test
  public void doCheckTemplateName() throws Exception {
    TemplatedCloudDeployment cloudDeployment = new TemplatedCloudDeployment(credentials.getId(),
        DEPLOYMENT_NAME, CONFIG_FILE, IMPORT_PATHS, null);
    AbstractCloudDeploymentDescriptor descriptor = cloudDeployment.getDescriptor();

    when(project.getName()).thenReturn("hello world");

    assertEquals(FormValidation.Kind.OK,
        descriptor.doCheckConfigFilePath("template", project).kind);
    assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckConfigFilePath("", project).kind);
    // leading slash
    assertEquals(FormValidation.Kind.OK, descriptor.doCheckConfigFilePath("/baz", project).kind);
    // invalid character
    assertEquals(FormValidation.Kind.OK, descriptor.doCheckConfigFilePath("foo/bar", project).kind);
    // Successful resolve
    assertEquals(FormValidation.Kind.OK,
        descriptor.doCheckConfigFilePath("$BUILD_NUMBER", project).kind);
    // UN-successful resolve
    assertEquals(FormValidation.Kind.OK, descriptor.doCheckConfigFilePath("$world", project).kind);

    // Check that we fail with a component consisting of a bad value.
    assertEquals(FormValidation.Kind.OK,
        descriptor.doCheckConfigFilePath("$JOB_NAME", project).kind);
    // Lastly check that when the container is specified with a good
    // value we accept it.
    when(project.getName()).thenReturn("hello_world");
    assertEquals(FormValidation.Kind.OK,
        descriptor.doCheckConfigFilePath("$JOB_NAME", project).kind);
  }

  @Test
  public void doCheckDeploymentNameTest() throws Exception {
    TemplatedCloudDeployment cloudDeployment = new TemplatedCloudDeployment(credentials.getId(),
        DEPLOYMENT_NAME, CONFIG_FILE, IMPORT_PATHS, null);
    AbstractCloudDeploymentDescriptor descriptor = cloudDeployment.getDescriptor();

    when(project.getName()).thenReturn("hello world");

    assertEquals(FormValidation.Kind.OK,
        descriptor.doCheckDeploymentName("deployment", project).kind);
    assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckDeploymentName("", project).kind);
    // Too many components
    assertEquals(FormValidation.Kind.ERROR,
        descriptor.doCheckDeploymentName("bar/baz/blah", project).kind);
    // Still too many components
    assertEquals(FormValidation.Kind.ERROR,
        descriptor.doCheckDeploymentName("foo/bar", project).kind);
    // Successful resolve
    assertEquals(FormValidation.Kind.OK,
        descriptor.doCheckDeploymentName("$BUILD_NUMBER", project).kind);
    // UN-successful resolve
    assertEquals(FormValidation.Kind.ERROR,
        descriptor.doCheckDeploymentName("$world", project).kind);
    // Successful composite resolve.
    assertEquals(FormValidation.Kind.OK,
        descriptor.doCheckDeploymentName("foo-$BUILD_NUMBER-$BUILD_NUMBER-bar", project).kind);
    // Unsuccessful composite resolve.
    assertEquals(FormValidation.Kind.ERROR,
        descriptor.doCheckDeploymentName("foo-$BUILD_NUMBER-$world-bar", project).kind);

    // Check that when the container is specified with a bad
    // value we still error out.
    assertEquals(FormValidation.Kind.ERROR,
        descriptor.doCheckDeploymentName("$JOB_NAME", project).kind);
    // Lastly check that when the container is specified with a good
    // value we accept it.
    when(project.getName()).thenReturn("hello_world");
    assertEquals(FormValidation.Kind.OK,
        descriptor.doCheckDeploymentName("$JOB_NAME", project).kind);
  }

  @Test
  public void doCheckTemplateFileTest() throws Exception {
    TemplatedCloudDeployment cloudDeployment = new TemplatedCloudDeployment(credentials.getId(),
        DEPLOYMENT_NAME, CONFIG_FILE, IMPORT_PATHS, null);
    TemplatedCloudDeployment.DescriptorImpl descriptor =
        (TemplatedCloudDeployment.DescriptorImpl) cloudDeployment.getDescriptor();

    assertEquals(FormValidation.Kind.OK,
        descriptor.doCheckConfigFilePath("/path/to/foo.yaml").kind);
    assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckConfigFilePath("").kind);
  }

  @Test
  public void testSplitNoImportPath() {
    assertThat(newArrayList(TemplatedCloudDeployment.commaSeparatedToList(""))).isEmpty();
  }

  @Test
  public void testSplitOneImportPath() {
    assertThat(newArrayList(TemplatedCloudDeployment.commaSeparatedToList("path1")))
        .containsExactly("path1");
  }

  @Test
  public void testSplitTwoImportPath() {
    assertThat(newArrayList(TemplatedCloudDeployment.commaSeparatedToList("path1, to/path/2")))
        .containsExactly("path1", "to/path/2");
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
