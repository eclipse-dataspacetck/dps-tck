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

package org.eclipse.dataspacetck.dps.verification.dataplane;

import org.eclipse.dataspacetck.api.system.MandatoryTest;
import org.eclipse.dataspacetck.api.system.TestSequenceDiagram;
import org.eclipse.dataspacetck.core.api.system.ConfigParam;
import org.eclipse.dataspacetck.core.api.system.Inject;
import org.eclipse.dataspacetck.core.system.SystemBootstrapExtension;
import org.eclipse.dataspacetck.dps.system.pipeline.DataPlaneSignalingPipeline;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

import static java.util.UUID.randomUUID;

/**
 * Verifies compliance of a provider data plane with the Data Plane Signaling specification.
 *
 * <p>The TCK acts as the <em>provider control plane</em> (sending DPS messages to the CUT)
 * and validates that the provider data plane under test handles them correctly and sends
 * proper callbacks.
 */
@Tag("base-compliance")
@DisplayName("DP_P: Data plane provider signaling scenarios")
@ExtendWith(SystemBootstrapExtension.class)
public class DataPlaneProviderSignalingTest {

    @Inject
    protected DataPlaneSignalingPipeline signalingPipeline;

    @ConfigParam
    protected String agreementId = randomUUID().toString();

    @ConfigParam
    protected String datasetId = randomUUID().toString();

    @MandatoryTest
    @DisplayName("DP_P:01-01: Verify DataFlowStartMessage is handled and completed notification is accepted")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (provider control plane)
            participant CUT as Provider Data-Plane Under Test

            TCK->>CUT: DataFlowStartMessage (POST /dataflows/start)
            CUT-->>TCK: 200 OK + DataFlowStatusMessage (state=STARTED)
            TCK->>CUT: Completed notification (POST /dataflows/{id}/completed)
            CUT-->>TCK: 200 OK
            """)
    public void dp_p_01_01() {
        signalingPipeline
                .sendDataFlowStartMessage(agreementId, datasetId)
                .sendDataFlowCompletedNotification()
                .execute();
    }

    @MandatoryTest
    @DisplayName("DP_P:01-02: Verify DataFlowStartMessage is handled and terminate notification is accepted")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (provider control plane)
            participant CUT as Provider Data-Plane Under Test

            TCK->>CUT: DataFlowStartMessage (POST /dataflows/start)
            CUT-->>TCK: 200 OK + DataFlowStatusMessage (state=STARTED)
            TCK->>CUT: DataFlowTerminateMessage (POST /dataflows/{id}/terminate)
            CUT-->>TCK: 200 OK
            """)
    public void dp_p_01_02() {
        signalingPipeline
                .sendDataFlowStartMessage(agreementId, datasetId)
                .sendDataFlowTerminateMessage()
                .execute();
    }

    @MandatoryTest
    @DisplayName("DP_P:02-01: Verify DataFlowSuspendMessage and DataFlowResumeMessage are handled and completed notification is accepted")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (provider control plane)
            participant CUT as Provider Data-Plane Under Test

            TCK->>CUT: DataFlowStartMessage (POST /dataflows/start)
            CUT-->>TCK: 200 OK + DataFlowStatusMessage (state=STARTED)
            TCK->>CUT: DataFlowSuspendMessage (POST /dataflows/{id}/suspend)
            CUT-->>TCK: 200 OK
            TCK->>CUT: DataFlowResumeMessage (POST /dataflows/{id}/resume)
            CUT-->>TCK: 200 OK + DataFlowStatusMessage (state=STARTED)
            TCK->>CUT: Completed notification (POST /dataflows/{id}/completed)
            CUT-->>TCK: 200 OK
            """)
    public void dp_p_02_01() {
        signalingPipeline
                .sendDataFlowStartMessage(agreementId, datasetId)
                .sendDataFlowSuspendMessage()
                .sendDataFlowResumeMessage()
                .sendDataFlowCompletedNotification()
                .execute();
    }

    @MandatoryTest
    @DisplayName("DP_P:02-02: Verify DataFlowSuspendMessage is handled and terminate notification is accepted")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (provider control plane)
            participant CUT as Provider Data-Plane Under Test

            TCK->>CUT: DataFlowStartMessage (POST /dataflows/start)
            CUT-->>TCK: 200 OK + DataFlowStatusMessage (state=STARTED)
            TCK->>CUT: DataFlowSuspendMessage (POST /dataflows/{id}/suspend)
            CUT-->>TCK: 200 OK
            TCK->>CUT: DataFlowTerminateMessage (POST /dataflows/{id}/terminate)
            CUT-->>TCK: 200 OK
            """)
    public void dp_p_02_02() {
        signalingPipeline
                .sendDataFlowStartMessage(agreementId, datasetId)
                .sendDataFlowSuspendMessage()
                .sendDataFlowTerminateMessage()
                .execute();
    }

    @MandatoryTest
    @DisplayName("DP_P:03-01: Verify data plane sends /dataflow/completed callback after wire transfer is done")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (provider control plane)
            participant CUT as Provider Data-Plane Under Test

            TCK->>CUT: DataFlowStartMessage (POST /dataflows/start)
            CUT-->>TCK: 200 OK + DataFlowStatusMessage (state=STARTED)
            CUT->>TCK: DataFlowStatusMessage callback (POST /transfers/{processId}/dataflow/completed, state=COMPLETED)
            TCK-->>CUT: 200 OK
            """)
    public void dp_p_03_01() {
        signalingPipeline
                .expectCompletedCallback()
                .sendDataFlowStartMessage(agreementId, datasetId)
                .triggerDataPlaneCompletedCallback()
                .thenWaitForCompletedCallback()
                .execute();
    }

    @MandatoryTest
    @DisplayName("DP_P:04-01: Verify async DataFlowStartMessage: data plane responds 202+STARTING, sends /dataflow/started callback, and transfer completes")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (provider control plane)
            participant CUT as Provider Data-Plane Under Test

            TCK->>CUT: DataFlowStartMessage (POST /dataflows/start)
            CUT-->>TCK: 202 Accepted + DataFlowStatusMessage (state=STARTING)
            CUT->>TCK: DataFlowStatusMessage callback (POST /transfers/{processId}/dataflow/started, state=STARTED)
            TCK-->>CUT: 200 OK
            TCK->>CUT: Completed notification (POST /dataflows/{id}/completed)
            CUT-->>TCK: 200 OK
            """)
    public void dp_p_04_01() {
        signalingPipeline
                .expectStartedCallback()
                .sendDataFlowStartMessageAsync(agreementId, datasetId)
                .thenWaitForStartedCallback()
                .sendDataFlowCompletedNotification()
                .execute();
    }
}
