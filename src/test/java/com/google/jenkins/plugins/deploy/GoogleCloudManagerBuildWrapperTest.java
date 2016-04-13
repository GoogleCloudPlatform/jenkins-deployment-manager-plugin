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

import static com.google.jenkins.plugins.TestFixture.CONFIG_FILE;
import static com.google.jenkins.plugins.TestFixture.CONFIG_FILE_CONTENTS;
import static com.google.jenkins.plugins.TestFixture.CREDENTIALS_ID;
import static com.google.jenkins.plugins.TestFixture.DEPLOYMENT_NAME;
import static com.google.jenkins.plugins.TestFixture.IMPORT_PATHS;
import static com.google.jenkins.plugins.TestFixture.NAME;
import static com.google.jenkins.plugins.TestFixture.PROJECT_ID;
import static com.google.jenkins.plugins.TestFixture.TEMPLATE1_CONTENTS;
import static com.google.jenkins.plugins.TestFixture.TEMPLATE2_CONTENTS;
import static com.google.jenkins.plugins.deploy.OperationFactory.newDoneOperation;
import static com.google.jenkins.plugins.deploy.OperationFactory.newRunningOperation;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.google.api.services.deploymentmanager.DeploymentManager;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import com.google.jenkins.plugins.PathUtils;
import com.google.jenkins.plugins.TestFixture;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentialsModule;
import com.google.jenkins.plugins.manage.AbstractCloudDeployment;
import com.google.jenkins.plugins.manage.CloudDeploymentModule;
import com.google.jenkins.plugins.manage.TemplatedCloudDeployment;
import com.google.jenkins.plugins.util.MockExecutor;

import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.NameWith;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;

import hudson.FilePath;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.tasks.Shell;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.Verifier;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Tests for {@link GoogleCloudManagerBuildWrapper}.
 */
public class GoogleCloudManagerBuildWrapperTest {
  // Allow for testing using JUnit4, instead of JUnit3.
  @Rule
  public JenkinsRule jenkins = new JenkinsRule();

  @Rule
  public TemporaryFolder dir = new TemporaryFolder();

  @Mock
  private FakeGoogleRobotCredentials credentials;

  private FreeStyleProject project;

  private FilePath workspace;

  private FilePath configFilePath;

  private List<FilePath> importPaths;

  @Mock
  private AbstractCloudDeployment deployment;

  private MockExecutor executor = new MockExecutor();
  @Rule
  public Verifier verifySawAll = new Verifier() {
    @Override
    public void verify() {
      assertTrue(executor.sawAll());
      assertFalse(executor.sawUnexpected());
    }
  };

  /**
   * Simple wrapper for new TemplatedCloudDeployment() to reduce scope of changes to this test for
   * new constructor arguments out of scope to this test.
   */
  private TemplatedCloudDeployment getNewTestingTemplatedCloudDeployment(String credentialsId,
      String deploymentName, String configFile, String importPaths,
      @Nullable CloudDeploymentModule module) {
    return new TemplatedCloudDeployment(credentialsId, deploymentName, configFile, importPaths,
        module);
  }

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    when(credentials.getProjectId()).thenReturn(PROJECT_ID);
    when(credentials.getId()).thenReturn(CREDENTIALS_ID);
    SystemCredentialsProvider.getInstance().getCredentials().add(credentials);

    // Create a project to which we may attach our uploader.
    this.project = jenkins.createFreeStyleProject("test");

    this.workspace = new FilePath(PathUtils.makeTempDir(dir, "the", "workspace"));
    this.configFilePath = workspace.child("tmpl.yaml");
    this.importPaths = ImmutableList.of(workspace.child(TestFixture.IMPORT_PATH_1),
        workspace.child(TestFixture.IMPORT_PATH_2));
  }

  @Test
  public void roundtripTest() throws Exception {
    GoogleCloudManagerBuildWrapper deployer = new GoogleCloudManagerBuildWrapper(
        // In this context, go ahead and test the full constructor rather
        // than use the helper method.
        new TemplatedCloudDeployment(CREDENTIALS_ID, DEPLOYMENT_NAME, CONFIG_FILE, IMPORT_PATHS,
            null));

    project.getBuildersList().add(new Shell("echo foo > bar.txt"));
    project.getBuildWrappersList().add(deployer);

    HtmlForm form = jenkins.createWebClient().getPage(project, "configure").getFormByName("config");

    assertEquals(CONFIG_FILE, form.<HtmlTextInput>getInputByName("_.configFilePath").getText());
    assertEquals(IMPORT_PATHS, form.<HtmlTextInput>getInputByName("_.importPaths").getText());
    assertEquals(DEPLOYMENT_NAME, form.<HtmlTextInput>getInputByName("_.deploymentName").getText());

    // Submit the form and check that the values match our original construction
    jenkins.submit(form);
    deployer = project.getBuildWrappersList().get(GoogleCloudManagerBuildWrapper.class);

    assertEquals(CONFIG_FILE,
        ((TemplatedCloudDeployment) deployer.getDeployment()).getConfigFilePath());
    assertEquals(IMPORT_PATHS,
        ((TemplatedCloudDeployment) deployer.getDeployment()).getImportPaths());
    assertEquals(DEPLOYMENT_NAME, deployer.getDeployment().getDeploymentName());
  }

  @Test
  public void performDeployTest() throws Exception {
    configFilePath.write(CONFIG_FILE_CONTENTS, "UTF-8");
    importPaths.get(0).write(TEMPLATE1_CONTENTS, "UTF-8");
    importPaths.get(1).write(TEMPLATE2_CONTENTS, "UTF-8");

    // Hand the deployer a special builder that will intercept executions
    {
      executor.when(DeploymentManager.Deployments.Insert.class, newRunningOperation());
      executor.when(DeploymentManager.Operations.Get.class, newRunningOperation());
      executor.when(DeploymentManager.Operations.Get.class, newDoneOperation());
      executor.when(DeploymentManager.Deployments.Delete.class, newDoneOperation());
    }
    String remoteImportPaths = PathUtils.toRemotePaths(importPaths);
    GoogleCloudManagerBuildWrapper deployer = new GoogleCloudManagerBuildWrapper(
        getNewTestingTemplatedCloudDeployment(credentials.getId(), DEPLOYMENT_NAME,
            configFilePath.getRemote(), remoteImportPaths, new MockPluginModule(executor)));

    project.getBuildWrappersList().add(deployer);

    FreeStyleBuild build = project.scheduleBuild2(0).get();

    dumpLog(build);
    assertEquals(Result.SUCCESS, build.getResult());
  }

  @Test
  public void performDeployWithIOExceptionTest() throws Exception {
    configFilePath.write(CONFIG_FILE_CONTENTS, "UTF-8");
    importPaths.get(0).write(TEMPLATE1_CONTENTS, "UTF-8");
    importPaths.get(1).write(TEMPLATE2_CONTENTS, "UTF-8");

    // Hand the deployer a special builder that will intercept executions
    {
      // Construct a successful deployment response message.
      executor.throwWhen(DeploymentManager.Deployments.Insert.class, new IOException("test"));
      // Before dying, we must attempt to rollback the insertion to avoid
      // leaving around garbage.
      executor.when(DeploymentManager.Deployments.Delete.class, newDoneOperation());
    }
    List<FilePath> importPaths = this.importPaths;
    String remoteImportPaths = PathUtils.toRemotePaths(importPaths);
    GoogleCloudManagerBuildWrapper deployer = new GoogleCloudManagerBuildWrapper(
        getNewTestingTemplatedCloudDeployment(credentials.getId(), DEPLOYMENT_NAME,
            configFilePath.getRemote(), remoteImportPaths, new MockPluginModule(executor)));

    project.getBuildWrappersList().add(deployer);

    FreeStyleBuild build = project.scheduleBuild2(0).get();

    dumpLog(build);
    assertEquals(Result.FAILURE, build.getResult());
    assertThat(CharStreams.toString(new InputStreamReader(build.getLogInputStream())),
        containsString("Rollback successful"));
  }

  @Test
  public void performDeployWithIOExceptionDuringDeleteTest() throws Exception {
    configFilePath.write(CONFIG_FILE_CONTENTS, "UTF-8");
    importPaths.get(0).write(TEMPLATE1_CONTENTS, "UTF-8");
    importPaths.get(1).write(TEMPLATE2_CONTENTS, "UTF-8");

    // Hand the deployer a special builder that will intercept executions
    {
      executor.when(DeploymentManager.Deployments.Insert.class, newDoneOperation());
      executor.throwWhen(DeploymentManager.Deployments.Delete.class, new IOException("test"));
    }
    GoogleCloudManagerBuildWrapper deployer =
        new GoogleCloudManagerBuildWrapper(getNewTestingTemplatedCloudDeployment(
            credentials.getId(), DEPLOYMENT_NAME, configFilePath.getRemote(),
            PathUtils.toRemotePaths(importPaths), new MockPluginModule(executor)));

    project.getBuildWrappersList().add(deployer);

    FreeStyleBuild build = project.scheduleBuild2(0).get();

    dumpLog(build);
    assertEquals(Result.FAILURE, build.getResult());
  }

  private void dumpLog(Run<?, ?> run) throws IOException {
    BufferedReader reader = new BufferedReader(run.getLogReader());

    String line;
    while ((line = reader.readLine()) != null) {
      System.out.println(line);
    }
  }

  @NameWith(value = Namer.class, priority = 50)
  private abstract static class FakeGoogleRobotCredentials extends GoogleRobotCredentials {
    public FakeGoogleRobotCredentials(String a) {
      super(a, new GoogleRobotCredentialsModule());
    }
  }

  public static class Namer extends CredentialsNameProvider<FakeGoogleRobotCredentials> {
    public String getName(FakeGoogleRobotCredentials c) {
      return NAME;
    }
  }

  private static final class MockPluginModule extends CloudDeploymentModule {
    private final MockExecutor executor;

    public MockPluginModule(MockExecutor executor) {
      super(TemplatedCloudDeployment.class);
      this.executor = executor;
    }

    @Override
    public MockExecutor newExecutor() {
      return executor;
    }
  }

}
