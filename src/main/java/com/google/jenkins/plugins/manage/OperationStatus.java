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

import com.google.api.services.deploymentmanager.model.Operation;

/**
 * Represents the status of an operation
 */
public enum OperationStatus {

  PENDING, RUNNING, DONE;

  public boolean isStatusOf(Operation operation) {
    return this == valueOf(operation.getStatus());
  }

}
