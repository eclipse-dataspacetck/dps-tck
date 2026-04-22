/*
 *  Copyright (c) 2026 Think-it GmbH
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Think-it GmbH - initial API and implementation
 *
 */

plugins {
    application
}

dependencies {
    implementation(project(":dps-system"))
    implementation(libs.tck.dsp.core)
    implementation(libs.tck.dsp.tck.runtime)
    implementation(libs.junit.platform.launcher)

    testImplementation(project(":dps-testcases"))
    testImplementation(libs.assertj)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.platform.engine)
}

application {
    mainClass.set("org.eclipse.dataspacetck.dps.controlplane.suite.DpsTckSuite")
}