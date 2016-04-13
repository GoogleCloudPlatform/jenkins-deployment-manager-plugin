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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

import com.google.api.client.googleapis.services.json.AbstractGoogleJsonClientRequest;
import com.google.api.services.deploymentmanager.DeploymentManager;
import com.google.jenkins.plugins.credentials.oauth.GoogleOAuth2ScopeRequirement;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import com.google.jenkins.plugins.util.Executor;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link CloudDeploymentModule}.
 */
public class CloudDeploymentModuleTest {
  private static final String ROOT_URL = "http://root.url.com/";

  private static final String SERVICE_PATH = "foo/bar/baz/";

  private CloudDeploymentModule underTest;

  @Mock
  private GoogleRobotCredentials mockCredentials;

  @Mock
  private AbstractGoogleJsonClientRequest<Void> mockRequest;

  @Mock
  private AbstractCloudDeploymentDescriptor mockDescriptor;

  @Mock
  private GoogleOAuth2ScopeRequirement mockRequirement;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    underTest = new CloudDeploymentModule(TemplatedCloudDeployment.class) {
      @Override
      AbstractCloudDeploymentDescriptor getDescriptor() {
        return mockDescriptor;
      }

      @Override
      public GoogleOAuth2ScopeRequirement getRequirement() {
        return mockRequirement;
      }
    };
  }

  @Test
  public void testNewService() throws Exception {
    when(mockCredentials.getGoogleCredential(mockRequirement)).thenReturn(null);

    when(mockDescriptor.getRootUrl()).thenReturn(ROOT_URL);
    when(mockDescriptor.getServicePath()).thenReturn(SERVICE_PATH);

    DeploymentManager manager = underTest.newManager(mockCredentials);

    assertNotNull(manager);
    assertEquals(ROOT_URL, manager.getRootUrl());
    assertEquals(SERVICE_PATH, manager.getServicePath());
  }

  @Test
  public void testVanillaNewExecutor() throws Exception {
    Executor exec = underTest.newExecutor();

    assertNotNull(exec);
    when(mockRequest.execute()).thenReturn((Void) null);

    exec.execute(mockRequest);
  }
}
