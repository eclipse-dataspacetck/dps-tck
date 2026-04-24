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

package org.eclipse.dataspacetck.dps.verification.controlplane;

import org.eclipse.dataspacetck.api.system.MandatoryTest;
import org.eclipse.dataspacetck.api.system.TestSequenceDiagram;
import org.eclipse.dataspacetck.core.api.system.ConfigParam;
import org.eclipse.dataspacetck.core.api.system.Inject;
import org.eclipse.dataspacetck.core.api.verification.AbstractVerificationTest;
import org.eclipse.dataspacetck.dps.system.api.pipeline.ControlPlaneSignalingPipeline;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;

import static java.util.UUID.randomUUID;

@Tag("base-compliance")
@DisplayName("CP_C: Control plane consumer signaling scenarios")
public class ControlPlaneConsumerSignalingTest extends AbstractVerificationTest {

    @Inject
    protected ControlPlaneSignalingPipeline signalingPipeline;

    @ConfigParam
    protected String processId = randomUUID().toString();

    @ConfigParam
    protected String agreementId = randomUUID().toString();

    @ConfigParam
    protected String datasetId = randomUUID().toString();

    @MandatoryTest
    @DisplayName("CP_C:01-01: Verify DataFlowPrepareMessage is dispatched and completed notification is sent to the data plane")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (data plane)
            participant CUT as Control-Plane Under Test

            TCK->>CUT: Signal to start data flow preparation
            CUT->>TCK: DataFlowPrepareMessage (POST /dataflows/prepare)
            TCK-->>CUT: 200 OK + DataFlowStatusMessage (state=PREPARING)
            TCK->>CUT: Signal transfer process completion
            CUT->>TCK: Completed notification (POST /dataflows/{processId}/completed)
            """)
    public void cp_c_01_01() {
        signalingPipeline
                .expectDataFlowPrepareMessage()
                .triggerDataFlowPreparation(processId, agreementId, datasetId)
                .thenWaitForPrepareMessage()
                .thenWaitForTransferRequestMessage()
                .thenWaitForTransferToBeInState("REQUESTED")
                .sendTransferStartMessage(processId)
                .thenWaitForTransferToBeInState("STARTED")
                .expectDataFlowCompletedMessage(processId)
                .sendTransferCompletionMessage(processId)
                .thenWaitForTransferToBeInState("COMPLETED")
                .thenWaitForCompletedMessage()
                .execute();
    }

    @MandatoryTest
    @DisplayName("CP_C:01-02: Verify DataFlowPrepareMessage is dispatched and terminated is sent to the data plane")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (data plane)
            participant CUT as Control-Plane Under Test

            TCK->>CUT: Signal to start data flow preparation
            CUT->>TCK: DataFlowPrepareMessage (POST /dataflows/prepare)
            TCK-->>CUT: 200 OK + DataFlowStatusMessage (state=PREPARING)
            TCK->>CUT: Signal transfer process termination
            CUT->>TCK: Completed notification (POST /dataflows/{processId}/terminate)
            """)
    public void cp_c_01_02() {
        signalingPipeline
                .expectDataFlowPrepareMessage()
                .triggerDataFlowPreparation(processId, agreementId, datasetId)
                .thenWaitForPrepareMessage()
                .thenWaitForTransferRequestMessage()
                .thenWaitForTransferToBeInState("REQUESTED")
                .sendTransferStartMessage(processId)
                .thenWaitForTransferToBeInState("STARTED")
                .expectDataFlowTerminateMessage(processId)
                .sendTransferTerminationMessage(processId)
                .thenWaitForTransferToBeInState("TERMINATED")
                .thenWaitForTerminateMessage()
                .execute();
    }

    @MandatoryTest
    @DisplayName("CP_C:02-01: Verify DataFlowSuspendMessage and DataFlowResumeMessage are dispatched to the data plane and completed notification is sent")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (data plane)
            participant CUT as Control-Plane Under Test

            TCK->>CUT: Signal to start data flow preparation
            CUT->>TCK: DataFlowPrepareMessage (POST /dataflows/prepare)
            TCK-->>CUT: 200 OK + DataFlowStatusMessage (state=PREPARING)
            TCK->>CUT: Signal transfer process start
            TCK->>CUT: Signal transfer process suspension
            CUT->>TCK: DataFlowSuspendMessage (POST /dataflows/{processId}/suspend)
            TCK-->>CUT: 200 OK
            TCK->>CUT: Signal transfer process resumption
            CUT->>TCK: DataFlowResumeMessage (POST /dataflows/{processId}/resume)
            TCK-->>CUT: 200 OK
            TCK->>CUT: Signal transfer process completion
            CUT->>TCK: Completed notification (POST /dataflows/{processId}/completed)
            """)
    public void cp_c_02_01() {
        signalingPipeline
                .expectDataFlowPrepareMessage()
                .triggerDataFlowPreparation(processId, agreementId, datasetId)
                .thenWaitForPrepareMessage()
                .thenWaitForTransferRequestMessage()
                .thenWaitForTransferToBeInState("REQUESTED")
                .sendTransferStartMessage(processId)
                .thenWaitForTransferToBeInState("STARTED")
                .expectDataFlowSuspendMessage(processId)
                .sendTransferSuspensionMessage(processId)
                .thenWaitForSuspendMessage()
                .expectDataFlowResumeMessage(processId)
                .sendTransferResumptionMessage(processId)
                .thenWaitForResumeMessage()
                .expectDataFlowCompletedMessage(processId)
                .sendTransferCompletionMessage(processId)
                .thenWaitForTransferToBeInState("COMPLETED")
                .thenWaitForCompletedMessage()
                .execute();
    }

    @MandatoryTest
    @DisplayName("CP_C:02-02: Verify DataFlowSuspendMessage is dispatched to the data plane and terminated notification is sent")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (data plane)
            participant CUT as Control-Plane Under Test

            TCK->>CUT: Signal to start data flow preparation
            CUT->>TCK: DataFlowPrepareMessage (POST /dataflows/prepare)
            TCK-->>CUT: 200 OK + DataFlowStatusMessage (state=PREPARING)
            TCK->>CUT: Signal transfer process start
            TCK->>CUT: Signal transfer process suspension
            CUT->>TCK: DataFlowSuspendMessage (POST /dataflows/{processId}/suspend)
            TCK-->>CUT: 200 OK
            TCK->>CUT: Signal transfer process termination
            CUT->>TCK: Terminate notification (POST /dataflows/{processId}/terminate)
            """)
    public void cp_c_02_02() {
        signalingPipeline
                .expectDataFlowPrepareMessage()
                .triggerDataFlowPreparation(processId, agreementId, datasetId)
                .thenWaitForPrepareMessage()
                .thenWaitForTransferRequestMessage()
                .thenWaitForTransferToBeInState("REQUESTED")
                .sendTransferStartMessage(processId)
                .thenWaitForTransferToBeInState("STARTED")
                .expectDataFlowSuspendMessage(processId)
                .sendTransferSuspensionMessage(processId)
                .thenWaitForSuspendMessage()
                .expectDataFlowTerminateMessage(processId)
                .sendTransferTerminationMessage(processId)
                .thenWaitForTransferToBeInState("TERMINATED")
                .thenWaitForTerminateMessage()
                .execute();
    }

}
