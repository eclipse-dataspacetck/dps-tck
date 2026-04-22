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

package org.eclipse.dataspacetck.dps.controlplane.suite;

import org.eclipse.dataspacetck.dps.system.DpsSystemLauncher;
import org.eclipse.dataspacetck.runtime.TckRuntime;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.dataspacetck.core.api.system.SystemsConstants.TCK_PREFIX;

/**
 * Runs the DPS TCK test cases against the embedded local control plane connector.
 * No external service is required.
 */
public class DpsTckLocalTest {

    @Test
    void runAllTestCasesLocally() {
        var result = TckRuntime.Builder.newInstance()
                .launcher(DpsSystemLauncher.class)
                .properties(Map.of(
                        TCK_PREFIX + ".dps.local.connector", "true",
                        TCK_PREFIX + ".debug", "true"
                ))
                .addPackage("org.eclipse.dataspacetck.dps.verification.controlplane")
                .build()
                .execute();

        assertThat(result.getTestsStartedCount()).isGreaterThan(0);

        assertThat(result.getFailures())
                .as("DPS TCK local run should have no failures")
                .isEmpty();
    }
}
