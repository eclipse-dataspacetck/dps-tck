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
    implementation(libs.assertj)
    implementation(libs.tck.common.api)
    implementation(libs.tck.dsp.core)
    implementation(libs.jackson.databind)
    implementation(libs.okhttp)
    implementation(libs.awaitility)
    implementation(libs.junit.jupiter.api)
}