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
 * Verifies compliance of a consumer data plane with the Data Plane Signaling specification.
 *
 * <p>The TCK acts as the <em>consumer control plane</em> (sending DPS messages to the CUT)
 * and validates that the consumer data plane under test handles them correctly and sends
 * proper callbacks.
 */
@Tag("base-compliance")
@DisplayName("DP_C: Data plane consumer signaling scenarios")
@ExtendWith(SystemBootstrapExtension.class)
public class DataPlaneConsumerSignalingTest {

    @Inject
    protected DataPlaneSignalingPipeline signalingPipeline;

    @ConfigParam
    protected String agreementId = randomUUID().toString();

    @ConfigParam
    protected String datasetId = randomUUID().toString();

    @MandatoryTest
    @DisplayName("DP_C:01-01: Verify DataFlowPrepareMessage is handled and completed notification is accepted")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (consumer control plane)
            participant CUT as Consumer Data-Plane Under Test

            TCK->>CUT: DataFlowPrepareMessage (POST /dataflows/prepare)
            CUT-->>TCK: 200 OK + DataFlowStatusMessage (state=PREPARED)
            TCK->>CUT: DataFlowStartedNotificationMessage (POST /dataflows/{id}/started)
            CUT-->>TCK: 200 OK
            TCK->>CUT: Completed notification (POST /dataflows/{id}/completed)
            CUT-->>TCK: 200 OK
            """)
    public void dp_c_01_01() {
        signalingPipeline
                .sendDataFlowPrepareMessage(agreementId, datasetId)
                .sendDataFlowStartedNotification()
                .sendDataFlowCompletedNotification()
                .execute();
    }

    @MandatoryTest
    @DisplayName("DP_C:01-02: Verify DataFlowPrepareMessage is handled and terminate notification is accepted")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (consumer control plane)
            participant CUT as Consumer Data-Plane Under Test

            TCK->>CUT: DataFlowPrepareMessage (POST /dataflows/prepare)
            CUT-->>TCK: 200 OK + DataFlowStatusMessage (state=PREPARED)
            TCK->>CUT: DataFlowStartedNotificationMessage (POST /dataflows/{id}/started)
            CUT-->>TCK: 200 OK
            TCK->>CUT: DataFlowTerminateMessage (POST /dataflows/{id}/terminate)
            CUT-->>TCK: 200 OK
            """)
    public void dp_c_01_02() {
        signalingPipeline
                .sendDataFlowPrepareMessage(agreementId, datasetId)
                .sendDataFlowStartedNotification()
                .sendDataFlowTerminateMessage()
                .execute();
    }

    @MandatoryTest
    @DisplayName("DP_C:02-01: Verify DataFlowSuspendMessage and DataFlowResumeMessage are handled and completed notification is accepted")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (consumer control plane)
            participant CUT as Consumer Data-Plane Under Test

            TCK->>CUT: DataFlowPrepareMessage (POST /dataflows/prepare)
            CUT-->>TCK: 200 OK + DataFlowStatusMessage (state=PREPARED)
            TCK->>CUT: DataFlowStartedNotificationMessage (POST /dataflows/{id}/started)
            CUT-->>TCK: 200 OK
            TCK->>CUT: DataFlowSuspendMessage (POST /dataflows/{id}/suspend)
            CUT-->>TCK: 200 OK
            TCK->>CUT: DataFlowResumeMessage (POST /dataflows/{id}/resume)
            CUT-->>TCK: 200 OK + DataFlowStatusMessage (state=STARTED)
            TCK->>CUT: Completed notification (POST /dataflows/{id}/completed)
            CUT-->>TCK: 200 OK
            """)
    public void dp_c_02_01() {
        signalingPipeline
                .sendDataFlowPrepareMessage(agreementId, datasetId)
                .sendDataFlowStartedNotification()
                .sendDataFlowSuspendMessage()
                .sendDataFlowResumeMessage()
                .sendDataFlowCompletedNotification()
                .execute();
    }

    @MandatoryTest
    @DisplayName("DP_C:02-02: Verify DataFlowSuspendMessage is handled and terminate notification is accepted")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (consumer control plane)
            participant CUT as Consumer Data-Plane Under Test

            TCK->>CUT: DataFlowPrepareMessage (POST /dataflows/prepare)
            CUT-->>TCK: 200 OK + DataFlowStatusMessage (state=PREPARED)
            TCK->>CUT: DataFlowStartedNotificationMessage (POST /dataflows/{id}/started)
            CUT-->>TCK: 200 OK
            TCK->>CUT: DataFlowSuspendMessage (POST /dataflows/{id}/suspend)
            CUT-->>TCK: 200 OK
            TCK->>CUT: DataFlowTerminateMessage (POST /dataflows/{id}/terminate)
            CUT-->>TCK: 200 OK
            """)
    public void dp_c_02_02() {
        signalingPipeline
                .sendDataFlowPrepareMessage(agreementId, datasetId)
                .sendDataFlowStartedNotification()
                .sendDataFlowSuspendMessage()
                .sendDataFlowTerminateMessage()
                .execute();
    }

    @MandatoryTest
    @DisplayName("DP_C:03-01: Verify data plane sends /dataflow/completed callback after wire transfer is done")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (consumer control plane)
            participant CUT as Consumer Data-Plane Under Test

            TCK->>CUT: DataFlowPrepareMessage (POST /dataflows/prepare)
            CUT-->>TCK: 200 OK + DataFlowStatusMessage (state=PREPARED)
            TCK->>CUT: DataFlowStartedNotificationMessage (POST /dataflows/{id}/started)
            CUT-->>TCK: 200 OK
            CUT->>TCK: DataFlowStatusMessage callback (POST /transfers/{processId}/dataflow/completed, state=COMPLETED)
            TCK-->>CUT: 200 OK
            """)
    public void dp_c_03_01() {
        signalingPipeline
                .expectCompletedCallback()
                .sendDataFlowPrepareMessage(agreementId, datasetId)
                .sendDataFlowStartedNotification()
                .triggerDataPlaneCompletedCallback()
                .thenWaitForCompletedCallback()
                .execute();
    }

    @MandatoryTest
    @DisplayName("DP_C:04-01: Verify async DataFlowPrepareMessage: data plane responds 202+PREPARING, sends /dataflow/prepared callback, and transfer completes")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (consumer control plane)
            participant CUT as Consumer Data-Plane Under Test

            TCK->>CUT: DataFlowPrepareMessage (POST /dataflows/prepare)
            CUT-->>TCK: 202 Accepted + DataFlowStatusMessage (state=PREPARING)
            CUT->>TCK: DataFlowStatusMessage callback (POST /transfers/{processId}/dataflow/prepared, state=PREPARED)
            TCK-->>CUT: 200 OK
            TCK->>CUT: DataFlowStartedNotificationMessage (POST /dataflows/{id}/started)
            CUT-->>TCK: 200 OK
            TCK->>CUT: Completed notification (POST /dataflows/{id}/completed)
            CUT-->>TCK: 200 OK
            """)
    public void dp_c_04_01() {
        signalingPipeline
                .expectPreparedCallback()
                .sendDataFlowPrepareMessageAsync(agreementId, datasetId)
                .thenWaitForPreparedCallback()
                .sendDataFlowStartedNotification()
                .sendDataFlowCompletedNotification()
                .execute();
    }
}
