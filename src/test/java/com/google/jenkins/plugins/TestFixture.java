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
package com.google.jenkins.plugins;

/**
 * Creates the data common to the test classes.
 */
public class TestFixture {
  public static final String NAME = "Source (foo.com:bar-baz)";

  public static final String IMPORT_PATH_1 = "template1.jinja";

  public static final String IMPORT_PATH_2 = "template2.jinja";

  public static final String IMPORT_PATHS = IMPORT_PATH_1 + ", " + IMPORT_PATH_2;

  public static final String EMPTY_IMPORT_PATH = "";

  public static final String CONFIG_FILE = "foo/bar.yaml";

  public static final String PROJECT_ID = "foo.com:bar-baz";

  public static final String CREDENTIALS_ID = "12345";

  public static final String DEPLOYMENT_NAME = "deployment-name";

  public static final String CONFIG_FILE_CONTENTS = "resources:\n" + "- name: the-first-vm\n"
      + "  type: compute.v1.instance\n" + "  properties:\n" + "    zone: us-central1-f\n"
      + "    machineType: https://www.googleapis.com/compute/v1/projects/mygcpproject/zones/us-central1-f/machineTypes/f1-micro\n"
      + "    disks:\n" + "    - deviceName: boot\n" + "      type: PERSISTENT\n"
      + "      boot: true\n" + "      autoDelete: true\n" + "      initializeParams:\n"
      + "        sourceImage: https://www.googleapis.com/compute/v1/projects/debian-cloud/global/images/debian-7-wheezy-v20151104\n"
      + "    networkInterfaces:\n"
      + "    - network: https://www.googleapis.com/compute/v1/projects/mygcpproject/global/networks/default\n"
      + "      accessConfigs:\n" + "      - name: External NAT\n" + "        type: ONE_TO_ONE_NAT";

  public static final String TEMPLATE1_CONTENTS = "resources:\n" + "- name: my-vm-template1\n"
      + "  type: compute.v1.instance\n" + "  properties:\n" + "    zone: us-central1-a\n"
      + "    machineType: https://www.googleapis.com/compute/v1/projects/{{ env[\"project\"] }}/zones/us-central1-a/machineTypes/n1-standard-1\n"
      + "    disks:\n" + "    - deviceName: boot\n" + "      type: PERSISTENT\n"
      + "      boot: true\n" + "      autoDelete: true\n" + "      initializeParams:\n"
      + "        diskName: disk-created-by-cloud-config\n"
      + "        sourceImage: https://www.googleapis.com/compute/v1/projects/debian-cloud/global/images/debian-7-wheezy-v20151104\n"
      + "    networkInterfaces:\n"
      + "    - network: https://www.googleapis.com/compute/v1/projects/{{ env[\"project\"] }}/global/networks/default";

  public static final String TEMPLATE2_CONTENTS = "resources:\n" + "- name: my-vm-template2\n"
      + "  type: compute.v1.instance\n" + "  properties:\n" + "    zone: us-central1-b\n"
      + "    machineType: https://www.googleapis.com/compute/v1/projects/{{ env[\"project\"] }}/zones/us-central1-b/machineTypes/n1-standard-1\n"
      + "    disks:\n" + "    - deviceName: boot\n" + "      type: PERSISTENT\n"
      + "      boot: true\n" + "      autoDelete: true\n" + "      initializeParams:\n"
      + "        diskName: disk-created-by-cloud-config\n"
      + "        sourceImage: https://www.googleapis.com/compute/v1/projects/debian-cloud/global/images/debian-7-wheezy-v20151104\n"
      + "    networkInterfaces:\n"
      + "    - network: https://www.googleapis.com/compute/v1/projects/{{ env[\"project\"] }}/global/networks/default";
}
