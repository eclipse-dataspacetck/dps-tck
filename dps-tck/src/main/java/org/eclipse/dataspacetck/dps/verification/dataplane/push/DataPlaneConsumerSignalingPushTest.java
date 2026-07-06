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
 * Verifies compliance of a consumer data plane with the Data Plane Signaling specification for PUSH flows.
 *
 * <p>The TCK acts as the <em>consumer control plane</em> (sending DPS messages to the CUT)
 * and validates that the consumer data plane under test handles them correctly and sends
 * proper callbacks.
 *
 * <p>In push flows the consumer data plane prepares an endpoint and returns a {@code DataAddress}
 * that the provider data plane will use to push data to the consumer.
 */
@Tag("base-compliance")
@DisplayName("DP_C_PUSH: Data plane consumer signaling push flows")
@ExtendWith(SystemBootstrapExtension.class)
public class DataPlaneConsumerSignalingPushTest {

    @Inject
    protected DataPlaneSignalingPipeline signalingPipeline;

    @ConfigParam
    protected String agreementId = randomUUID().toString();

    @ConfigParam
    protected String datasetId = randomUUID().toString();

    @ConfigParam
    protected String profile = "http-push";

    @MandatoryTest
    @Tag("sync")
    @DisplayName("DP_C_PUSH:01-01: Verify DataFlowPrepareMessage is handled and a DataAddress is returned, then started notification is accepted")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (consumer control plane)
            participant CUT as Consumer Data-Plane Under Test

            TCK->>CUT: DataFlowPrepareMessage (POST /dataflows/prepare)
            CUT-->>TCK: 200 OK + DataFlowStatusMessage (state=PREPARED) with non-null DataAddress
            TCK->>CUT: DataFlowStartedNotificationMessage (POST /dataflows/{id}/started)
            CUT-->>TCK: 200 OK
            """)
    public void dp_c_push_01_01() {
        signalingPipeline
                .sendDataFlowPrepareMessage(agreementId, datasetId, profile)
                .expectReceivedDataAddressToBeNonNull()
                .sendDataFlowStartedNotification()
                .thenWaitForDataFlowToBeInState("STARTED")
                .execute();
    }

    @MandatoryTest
    @Tag("sync")
    @DisplayName("DP_C_PUSH:01-02: Verify DataFlowPrepareMessage is handled and terminate message is accepted")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (consumer control plane)
            participant CUT as Consumer Data-Plane Under Test

            TCK->>CUT: DataFlowPrepareMessage (POST /dataflows/prepare)
            CUT-->>TCK: 200 OK + DataFlowStatusMessage (state=PREPARED) with non-null DataAddress
            TCK->>CUT: DataFlowStartedNotificationMessage (POST /dataflows/{id}/started)
            CUT-->>TCK: 200 OK
            TCK->>CUT: DataFlowTerminateMessage (POST /dataflows/{id}/terminate)
            CUT-->>TCK: 200 OK
            """)
    public void dp_c_push_01_02() {
        signalingPipeline
                .sendDataFlowPrepareMessage(agreementId, datasetId, profile)
                .expectReceivedDataAddressToBeNonNull()
                .sendDataFlowStartedNotification()
                .sendDataFlowTerminateMessage()
                .thenWaitForDataFlowToBeInState("TERMINATED")
                .execute();
    }

    @MandatoryTest
    @Tag("sync")
    @DisplayName("DP_C_PUSH:02-01: Verify DataFlowSuspendMessage and DataFlowResumeMessage are handled")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (consumer control plane)
            participant CUT as Consumer Data-Plane Under Test

            TCK->>CUT: DataFlowPrepareMessage (POST /dataflows/prepare)
            CUT-->>TCK: 200 OK + DataFlowStatusMessage (state=PREPARED) with non-null DataAddress
            TCK->>CUT: DataFlowStartedNotificationMessage (POST /dataflows/{id}/started)
            CUT-->>TCK: 200 OK
            TCK->>CUT: DataFlowSuspendMessage (POST /dataflows/{id}/suspend)
            CUT-->>TCK: 200 OK
            TCK->>CUT: DataFlowResumeMessage (POST /dataflows/{id}/resume)
            CUT-->>TCK: 200 OK + DataFlowStatusMessage (state=STARTED)
            """)
    public void dp_c_push_02_01() {
        signalingPipeline
                .sendDataFlowPrepareMessage(agreementId, datasetId, profile)
                .expectReceivedDataAddressToBeNonNull()
                .sendDataFlowStartedNotification()
                .sendDataFlowSuspendMessage()
                .thenWaitForDataFlowToBeInState("SUSPENDED")
                .sendDataFlowResumeMessage()
                .thenWaitForDataFlowToBeInState("STARTED")
                .execute();
    }

    @MandatoryTest
    @Tag("sync")
    @DisplayName("DP_C_PUSH:02-02: Verify DataFlowSuspendMessage is handled and terminate notification is accepted")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (consumer control plane)
            participant CUT as Consumer Data-Plane Under Test

            TCK->>CUT: DataFlowPrepareMessage (POST /dataflows/prepare)
            CUT-->>TCK: 200 OK + DataFlowStatusMessage (state=PREPARED) with non-null DataAddress
            TCK->>CUT: DataFlowStartedNotificationMessage (POST /dataflows/{id}/started)
            CUT-->>TCK: 200 OK
            TCK->>CUT: DataFlowSuspendMessage (POST /dataflows/{id}/suspend)
            CUT-->>TCK: 200 OK
            TCK->>CUT: DataFlowTerminateMessage (POST /dataflows/{id}/terminate)
            CUT-->>TCK: 200 OK
            """)
    public void dp_c_push_02_02() {
        signalingPipeline
                .sendDataFlowPrepareMessage(agreementId, datasetId, profile)
                .expectReceivedDataAddressToBeNonNull()
                .sendDataFlowStartedNotification()
                .sendDataFlowSuspendMessage()
                .thenWaitForDataFlowToBeInState("SUSPENDED")
                .sendDataFlowTerminateMessage()
                .thenWaitForDataFlowToBeInState("TERMINATED")
                .execute();
    }

    @MandatoryTest
    @Tag("sync")
    @DisplayName("DP_C_PUSH:03-01: Verify data plane transitions to COMPLETED after completed notification is received")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (consumer control plane)
            participant CUT as Consumer Data-Plane Under Test

            TCK->>CUT: DataFlowPrepareMessage (POST /dataflows/prepare)
            CUT-->>TCK: 200 OK + DataFlowStatusMessage (state=PREPARED) with non-null DataAddress
            TCK->>CUT: DataFlowStartedNotificationMessage (POST /dataflows/{id}/started)
            CUT-->>TCK: 200 OK
            TCK->>CUT: Completed notification (POST /dataflows/{id}/completed)
            CUT-->>TCK: 200 OK
            """)
    public void dp_c_push_03_01() {
        signalingPipeline
                .sendDataFlowPrepareMessage(agreementId, datasetId, profile)
                .expectReceivedDataAddressToBeNonNull()
                .sendDataFlowStartedNotification()
                .thenWaitForDataFlowToBeInState("STARTED")
                .sendDataFlowCompletedNotification()
                .thenWaitForDataFlowToBeInState("COMPLETED")
                .execute();
    }

    @MandatoryTest
    @Tag("async")
    @DisplayName("DP_C_PUSH:04-01: Verify async DataFlowPrepareMessage: data plane responds 202+PREPARING, sends /dataflow/prepared callback with DataAddress")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (consumer control plane)
            participant CUT as Consumer Data-Plane Under Test

            TCK->>CUT: DataFlowPrepareMessage (POST /dataflows/prepare)
            CUT-->>TCK: 202 Accepted + DataFlowStatusMessage (state=PREPARING)
            CUT->>TCK: DataFlowStatusMessage callback (POST /transfers/{processId}/dataflow/prepared, state=PREPARED)
            TCK-->>CUT: 200 OK
            """)
    public void dp_c_push_04_01() {
        signalingPipeline
                .expectPreparedCallback()
                .sendDataFlowPrepareMessageAsync(agreementId, datasetId, profile)
                .thenWaitForPreparedCallback()
                .thenWaitForDataFlowToBeInState("PREPARED")
                .execute();
    }
}
