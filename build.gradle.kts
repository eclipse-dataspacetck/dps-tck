import org.eclipse.dataspacetck.gradle.tckbuild.extensions.TckBuildExtension

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
    `java-library`
    `maven-publish`
    signing
    checkstyle
    alias(libs.plugins.tck.build)
}

buildscript {
    dependencies {
        val version: String by project
        classpath("org.eclipse.dataspacetck.build.tck-build:org.eclipse.dataspacetck.build.tck-build.gradle.plugin:$version")
        classpath("org.eclipse.dataspacetck.build.tck-generator:org.eclipse.dataspacetck.build.tck-generator.gradle.plugin:$version")
    }
}

allprojects {
    apply(plugin = "java-library")
    apply(plugin = "checkstyle")
    apply(plugin = "maven-publish")
    apply(plugin = "signing")
    apply(plugin = "org.eclipse.dataspacetck.build.tck-build")

    configure<TckBuildExtension> {
        pom {
            scmConnection = "https://github.com/eclipse-dataspacetck/dps-tck.git"
            scmUrl = "scm:git:git@github.com:eclipse-dataspacetck/dps-tck.git"
            projectName = project.name
            description = "DPS Technology Compatibility Kit"
            projectUrl = "https://projects.eclipse.org/projects/technology.dataspacetck"
        }
    }

    tasks.test {
        useJUnitPlatform()
    }

}

// needed for running the dash tool
tasks.register("allDependencies", DependencyReportTask::class)

// disallow any errors
checkstyle {
    maxErrors = 0
}