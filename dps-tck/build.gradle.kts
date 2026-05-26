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

val dpsSpecVersion: String by project

val dpsSchemaBaseUrl = "https://eclipse-dataplane-signaling.github.io/dataplane-signaling/$dpsSpecVersion/schemas"
val dspDataAddressSchemaUrl = "https://w3id.org/dspace/2025/1/transfer/data-address-schema.json"

val dpsSchemaNames = listOf(
    "DataFlowBaseMessage.schema.json",
    "DataFlowPrepareMessage.schema.json",
    "DataFlowStartMessage.schema.json",
    "DataFlowStartedNotificationMessage.schema.json",
    "DataFlowTerminateMessage.schema.json",
    "DataFlowSuspendMessage.schema.json",
    "DataFlowResumeMessage.schema.json",
    "DataFlowStatusMessage.schema.json",
    "DataFlowStatusResponseMessage.schema.json",
    "AgreementResponse.schema.json",
    "AuthorizationEntry.schema.json",
    "ControlPlaneRegistrationMessage.schema.json",
    "DataPlaneRegistrationMessage.schema.json"
)

val generatedResourcesDir = layout.buildDirectory.dir("generated-resources")

val downloadDpsSchemas by tasks.registering {
    outputs.dir(generatedResourcesDir)

    doLast {
        val schemaDir = generatedResourcesDir.get().dir("schema").asFile
        schemaDir.mkdirs()
        val dspDir = File(schemaDir, "dsp")
        dspDir.mkdirs()

        dpsSchemaNames.forEach { name ->
            val url = "$dpsSchemaBaseUrl/$name"
            logger.lifecycle("Downloading schema: $url")
            File(schemaDir, name).writeBytes(uri(url).toURL().readBytes())
        }

        logger.lifecycle("Downloading schema: $dspDataAddressSchemaUrl")
        File(dspDir, "data-address-schema.json").writeBytes(uri(dspDataAddressSchemaUrl).toURL().readBytes())
    }
}

sourceSets {
    main {
        resources {
            srcDir(generatedResourcesDir)
        }
    }
}

tasks.processResources {
    dependsOn(downloadDpsSchemas)
}

tasks.withType<Jar>().matching { it.name == "sourcesJar" }.configureEach {
    dependsOn(downloadDpsSchemas)
}

dependencies {
    api(libs.tck.common.api)
    api(libs.tck.core)
    api(libs.tck.runtime)

    implementation(libs.assertj)
    implementation(libs.awaitility)
    implementation(libs.okhttp)
    implementation(libs.jackson.databind)
    implementation(libs.json.schema.validator)
    implementation(libs.junit.jupiter.api)
    implementation(libs.junit.platform.launcher)

    testRuntimeOnly(libs.junit.platform.engine)
}

application {
    mainClass.set("org.eclipse.dataspacetck.dps.controlplane.suite.DpsTckSuite")
}