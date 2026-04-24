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

/**
 * Verifies compliance of a consumer control plane with the Data Plane Signaling specification.
 *
 * <p>The TCK acts simultaneously as the <em>consumer data plane</em> (receiving DPS signals from the CUT)
 * and as the <em>provider control plane</em> (exchanging DSP messages with the CUT).
 *
 * <p>Interactions covered:
 * <ul>
 *   <li>{@code POST /dataflows/prepare} — DataFlowPrepareMessage dispatch</li>
 *   <li>{@code POST /dataflows/{id}/suspend} — DataFlowSuspendMessage dispatch</li>
 *   <li>{@code POST /dataflows/{id}/resume} — DataFlowResumeMessage dispatch</li>
 *   <li>{@code POST /dataflows/{id}/completed} — completed notification dispatch</li>
 *   <li>{@code POST /dataflows/{id}/terminate} — terminate notification dispatch</li>
 * </ul>
 *
 * <p>Interactions <strong>not yet covered</strong> (require pipeline extension): TODO!
 * <ul>
 *   <li>{@code POST /dataflows/{id}/started} — DataFlowStartedNotificationMessage that the consumer CP
 *       must send to the consumer DP after receiving a DSP TransferStartMessage</li>
 *   <li>Asynchronous prepare/start transitions (HTTP 202 + {@code /dataflow/prepared} callback)</li>
 * </ul>
 */
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
            participant TCK as Technology Compatibility Kit (consumer data plane + provider CP)
            participant CUT as Consumer Control-Plane Under Test

            TCK->>CUT: Trigger data flow preparation (internal signal)
            CUT->>TCK: DataFlowPrepareMessage (POST /dataflows/prepare)
            TCK-->>CUT: 200 OK + DataFlowStatusMessage (state=PREPARING)
            CUT->>TCK: DSP TransferRequestMessage (POST /transfers/request)
            TCK-->>CUT: 200 OK + providerPid
            TCK->>CUT: DSP TransferStartMessage
            CUT->>TCK: DataFlowStartedNotificationMessage (POST /dataflows/{processId}/started) [not yet verified]
            TCK->>CUT: DSP TransferCompletionMessage
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
    @DisplayName("CP_C:01-02: Verify DataFlowPrepareMessage is dispatched and terminate notification is sent to the data plane")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (consumer data plane + provider CP)
            participant CUT as Consumer Control-Plane Under Test

            TCK->>CUT: Trigger data flow preparation (internal signal)
            CUT->>TCK: DataFlowPrepareMessage (POST /dataflows/prepare)
            TCK-->>CUT: 200 OK + DataFlowStatusMessage (state=PREPARING)
            CUT->>TCK: DSP TransferRequestMessage (POST /transfers/request)
            TCK-->>CUT: 200 OK + providerPid
            TCK->>CUT: DSP TransferStartMessage
            CUT->>TCK: DataFlowStartedNotificationMessage (POST /dataflows/{processId}/started) [not yet verified]
            TCK->>CUT: DSP TransferTerminationMessage
            CUT->>TCK: Terminate notification (POST /dataflows/{processId}/terminate)
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
            participant TCK as Technology Compatibility Kit (consumer data plane + provider CP)
            participant CUT as Consumer Control-Plane Under Test

            TCK->>CUT: Trigger data flow preparation (internal signal)
            CUT->>TCK: DataFlowPrepareMessage (POST /dataflows/prepare)
            TCK-->>CUT: 200 OK + DataFlowStatusMessage (state=PREPARING)
            CUT->>TCK: DSP TransferRequestMessage (POST /transfers/request)
            TCK-->>CUT: 200 OK + providerPid
            TCK->>CUT: DSP TransferStartMessage
            CUT->>TCK: DataFlowStartedNotificationMessage (POST /dataflows/{processId}/started) [not yet verified]
            TCK->>CUT: DSP TransferSuspensionMessage
            CUT->>TCK: DataFlowSuspendMessage (POST /dataflows/{processId}/suspend)
            TCK-->>CUT: 200 OK
            TCK->>CUT: DSP TransferResumptionMessage
            CUT->>TCK: DataFlowResumeMessage (POST /dataflows/{processId}/resume)
            TCK-->>CUT: 200 OK
            TCK->>CUT: DSP TransferCompletionMessage
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
    @DisplayName("CP_C:02-02: Verify DataFlowSuspendMessage is dispatched to the data plane and terminate notification is sent")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (consumer data plane + provider CP)
            participant CUT as Consumer Control-Plane Under Test

            TCK->>CUT: Trigger data flow preparation (internal signal)
            CUT->>TCK: DataFlowPrepareMessage (POST /dataflows/prepare)
            TCK-->>CUT: 200 OK + DataFlowStatusMessage (state=PREPARING)
            CUT->>TCK: DSP TransferRequestMessage (POST /transfers/request)
            TCK-->>CUT: 200 OK + providerPid
            TCK->>CUT: DSP TransferStartMessage
            CUT->>TCK: DataFlowStartedNotificationMessage (POST /dataflows/{processId}/started) [not yet verified]
            TCK->>CUT: DSP TransferSuspensionMessage
            CUT->>TCK: DataFlowSuspendMessage (POST /dataflows/{processId}/suspend)
            TCK-->>CUT: 200 OK
            TCK->>CUT: DSP TransferTerminationMessage
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
