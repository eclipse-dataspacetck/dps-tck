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

package org.eclipse.dataspacetck.dps.verification.dataplane.push;

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
 * Verifies compliance of a provider data plane with the Data Plane Signaling specification for PUSH flows.
 *
 * <p>The TCK acts as the <em>provider control plane</em> (sending DPS messages to the CUT)
 * and validates that the provider data plane under test handles them correctly and sends
 * proper callbacks.
 *
 * <p>In push flows the provider data plane receives a {@code DataAddress} in the start message,
 * pointing to the consumer endpoint, and pushes data there. The start response does NOT include
 * a DataAddress (that is consumer-pull specific).
 */
@Tag("base-compliance")
@DisplayName("DP_P_PUSH: Data plane provider signaling push flows")
@ExtendWith(SystemBootstrapExtension.class)
public class DataPlaneProviderSignalingPushTest {

    @Inject
    protected DataPlaneSignalingPipeline signalingPipeline;

    @ConfigParam
    protected String agreementId = randomUUID().toString();

    @ConfigParam
    protected String datasetId = randomUUID().toString();

    @ConfigParam
    protected String transferType = "http-push";

    @MandatoryTest
    @DisplayName("DP_P_PUSH:01-01: Verify DataFlowStartMessage with DataAddress is handled")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (provider control plane)
            participant CUT as Provider Data-Plane Under Test

            TCK->>CUT: DataFlowStartMessage + DataAddress (POST /dataflows/start)
            CUT-->>TCK: 200 OK + DataFlowStatusMessage (state=STARTED) with null DataAddress
            """)
    public void dp_p_push_01_01() {
        signalingPipeline
                .sendDataFlowStartMessageWithDataAddress(agreementId, datasetId, transferType)
                .expectReceivedDataAddressToBeNull()
                .thenWaitForDataFlowToBeInState("STARTED")
                .execute();
    }

    @MandatoryTest
    @DisplayName("DP_P_PUSH:01-02: Verify DataFlowStartMessage with DataAddress is handled and terminate notification is accepted")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (provider control plane)
            participant CUT as Provider Data-Plane Under Test

            TCK->>CUT: DataFlowStartMessage + DataAddress (POST /dataflows/start)
            CUT-->>TCK: 200 OK + DataFlowStatusMessage (state=STARTED)
            TCK->>CUT: DataFlowTerminateMessage (POST /dataflows/{id}/terminate)
            CUT-->>TCK: 200 OK
            """)
    public void dp_p_push_01_02() {
        signalingPipeline
                .sendDataFlowStartMessageWithDataAddress(agreementId, datasetId, transferType)
                .expectReceivedDataAddressToBeNull()
                .thenWaitForDataFlowToBeInState("STARTED")
                .sendDataFlowTerminateMessage()
                .thenWaitForDataFlowToBeInState("TERMINATED")
                .execute();
    }

    @MandatoryTest
    @DisplayName("DP_P_PUSH:02-01: Verify DataFlowSuspendMessage and DataFlowResumeMessage are handled")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (provider control plane)
            participant CUT as Provider Data-Plane Under Test

            TCK->>CUT: DataFlowStartMessage + DataAddress (POST /dataflows/start)
            CUT-->>TCK: 200 OK + DataFlowStatusMessage (state=STARTED)
            TCK->>CUT: DataFlowSuspendMessage (POST /dataflows/{id}/suspend)
            CUT-->>TCK: 200 OK
            TCK->>CUT: DataFlowResumeMessage (POST /dataflows/{id}/resume)
            CUT-->>TCK: 200 OK + DataFlowStatusMessage (state=STARTED)
            """)
    public void dp_p_push_02_01() {
        signalingPipeline
                .sendDataFlowStartMessageWithDataAddress(agreementId, datasetId, transferType)
                .sendDataFlowSuspendMessage()
                .thenWaitForDataFlowToBeInState("SUSPENDED")
                .sendDataFlowResumeMessage()
                .thenWaitForDataFlowToBeInState("STARTED")
                .execute();
    }

    @MandatoryTest
    @DisplayName("DP_P_PUSH:02-02: Verify DataFlowSuspendMessage is handled and terminate notification is accepted")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (provider control plane)
            participant CUT as Provider Data-Plane Under Test

            TCK->>CUT: DataFlowStartMessage + DataAddress (POST /dataflows/start)
            CUT-->>TCK: 200 OK + DataFlowStatusMessage (state=STARTED)
            TCK->>CUT: DataFlowSuspendMessage (POST /dataflows/{id}/suspend)
            CUT-->>TCK: 200 OK
            TCK->>CUT: DataFlowTerminateMessage (POST /dataflows/{id}/terminate)
            CUT-->>TCK: 200 OK
            """)
    public void dp_p_push_02_02() {
        signalingPipeline
                .sendDataFlowStartMessageWithDataAddress(agreementId, datasetId, transferType)
                .sendDataFlowSuspendMessage()
                .thenWaitForDataFlowToBeInState("SUSPENDED")
                .sendDataFlowTerminateMessage()
                .thenWaitForDataFlowToBeInState("TERMINATED")
                .execute();
    }

    @MandatoryTest
    @DisplayName("DP_P_PUSH:03-01: Verify data plane sends /dataflow/completed callback after push transfer is done")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (provider control plane)
            participant CUT as Provider Data-Plane Under Test

            TCK->>CUT: DataFlowStartMessage + DataAddress (POST /dataflows/start)
            CUT-->>TCK: 200 OK + DataFlowStatusMessage (state=STARTED)
            CUT->>TCK: DataFlowStatusMessage callback (POST /transfers/{processId}/dataflow/completed, state=COMPLETED)
            TCK-->>CUT: 200 OK
            """)
    public void dp_p_push_03_01() {
        signalingPipeline
                .sendDataFlowStartMessageWithDataAddress(agreementId, datasetId, transferType)
                .thenWaitForDataFlowToBeInState("STARTED")
                .expectCompletedCallback()
                .triggerDataPlaneCompletedCallback()
                .thenWaitForCompletedCallback()
                .execute();
    }

    @MandatoryTest
    @DisplayName("DP_P_PUSH:04-01: Verify async DataFlowStartMessage: data plane responds 202+STARTING, sends /dataflow/started callback")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (provider control plane)
            participant CUT as Provider Data-Plane Under Test

            TCK->>CUT: DataFlowStartMessage + DataAddress (POST /dataflows/start)
            CUT-->>TCK: 202 Accepted + DataFlowStatusMessage (state=STARTING)
            CUT->>TCK: DataFlowStatusMessage callback (POST /transfers/{processId}/dataflow/started, state=STARTED)
            TCK-->>CUT: 200 OK
            """)
    public void dp_p_push_04_01() {
        signalingPipeline
                .expectStartedCallback()
                .sendDataFlowStartMessageWithDataAddressAsync(agreementId, datasetId, transferType)
                .thenWaitForStartedCallback()
                .thenWaitForDataFlowToBeInState("STARTED")
                .execute();
    }
}
