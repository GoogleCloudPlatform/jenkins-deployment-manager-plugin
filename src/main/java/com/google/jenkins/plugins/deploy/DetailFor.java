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

import hudson.tasks.BuildStep;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>
 * This annotation is intended to go on implementations of the {@link BuildStepDetailsProvider} to
 * indicate the kinds of build steps for which it provides details.
 * </p>
 * <p>
 * NOTE: This is modeled after the NameWith attribute available through the
 * <a href="https://wiki.jenkins-ci.org/display/JENKINS/Credentials+Plugin"> credentials plugin</a>.
 * </p>
 * <p>
 * TODO: We need prioritization, although I believe that can be achieved today with {@code
 * {@literal @}Extension(ordinal = XXX)}
 * </p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface DetailFor {
  /**
   * The class of {@link BuildStep}s for which the annotated class provides details.
   */
  Class<? extends BuildStep> value();
}
