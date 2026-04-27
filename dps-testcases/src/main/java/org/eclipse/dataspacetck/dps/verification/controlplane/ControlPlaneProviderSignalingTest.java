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
 * Verifies compliance of a provider control plane with the Data Plane Signaling specification.
 *
 * <p>The TCK acts simultaneously as the <em>provider data plane</em> (receiving DPS signals from the CUT)
 * and as the <em>consumer control plane</em> (initiating DSP transfer-process messages to the CUT).
 *
 * <p>Interactions covered:
 * <ul>
 *   <li>{@code POST /dataflows/start} — DataFlowStartMessage dispatch after DSP TransferRequestMessage</li>
 *   <li>{@code POST /dataflows/{id}/suspend} — DataFlowSuspendMessage dispatch after DSP TransferSuspensionMessage</li>
 *   <li>{@code POST /dataflows/{id}/completed} — completed notification dispatch after DSP TransferCompletionMessage</li>
 *   <li>{@code POST /dataflows/{id}/terminate} — terminate notification dispatch after DSP TransferTerminationMessage</li>
 * </ul>
 *
 * <p>Interactions <strong>not yet covered</strong> (require pipeline extension):
 * <ul>
 *   <li>Asynchronous start transition (HTTP 202 + {@code /dataflow/started} callback to the CP)</li>
 *   <li>The {@code /transfers/:transferId/dataflow/errored} callback from DP to CP on non-recoverable error</li>
 * </ul>
 */
@Tag("base-compliance")
@DisplayName("CP_P: Control plane provider signaling scenarios")
public class ControlPlaneProviderSignalingTest extends AbstractVerificationTest {

    @Inject
    protected ControlPlaneSignalingPipeline signalingPipeline;

    @ConfigParam
    protected String processId = randomUUID().toString();

    @ConfigParam
    protected String agreementId = randomUUID().toString();

    @MandatoryTest
    @DisplayName("CP_P:01-01: Verify DataFlowStartMessage is dispatched when a transfer request is received and completed notification is sent to the data plane")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (consumer CP + provider data plane)
            participant CUT as Provider Control-Plane Under Test

            TCK->>CUT: DSP TransferRequestMessage (POST /transfers/request)
            CUT->>TCK: DataFlowStartMessage (POST /dataflows/start)
            TCK-->>CUT: 200 OK + DataFlowStatusMessage (state=STARTED)
            TCK->>CUT: DSP TransferCompletionMessage (POST /transfers/{id}/completion)
            CUT->>TCK: Completed notification (POST /dataflows/{processId}/completed)
            """)
    public void cp_p_01_01() {
        signalingPipeline
                .expectDataFlowStartMessage()
                .sendTransferRequestMessage(agreementId, "HttpData-PULL")
                .thenWaitForDataFlowStartMessage()
                .expectDataFlowCompletedMessage(processId)
                .sendTransferCompletionMessage(processId)
                .thenWaitForCompletedMessage()
                .execute();
    }

    @MandatoryTest
    @DisplayName("CP_P:01-02: Verify DataFlowStartMessage is dispatched when a transfer request is received and terminate notification is sent to the data plane")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (consumer CP + provider data plane)
            participant CUT as Provider Control-Plane Under Test

            TCK->>CUT: DSP TransferRequestMessage (POST /transfers/request)
            CUT->>TCK: DataFlowStartMessage (POST /dataflows/start)
            TCK-->>CUT: 200 OK + DataFlowStatusMessage (state=STARTED)
            TCK->>CUT: DSP TransferTerminationMessage (POST /transfers/{id}/termination)
            CUT->>TCK: Terminate notification (POST /dataflows/{processId}/terminate)
            """)
    public void cp_p_01_02() {
        signalingPipeline
                .expectDataFlowStartMessage()
                .sendTransferRequestMessage(agreementId, "HttpData-PULL")
                .thenWaitForDataFlowStartMessage()
                .expectDataFlowTerminateMessage(processId)
                .sendTransferTerminationMessage(processId)
                .thenWaitForTerminateMessage()
                .execute();
    }

    @MandatoryTest
    @DisplayName("CP_P:02-01: Verify DataFlowSuspendMessage and DataFlowResumeMessage are dispatched when transfer is suspended and resumed, and completed notification is sent")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (consumer CP + provider data plane)
            participant CUT as Provider Control-Plane Under Test

            TCK->>CUT: DSP TransferRequestMessage (POST /transfers/request)
            CUT->>TCK: DataFlowStartMessage (POST /dataflows/start)
            TCK-->>CUT: 200 OK + DataFlowStatusMessage (state=STARTED)
            TCK->>CUT: DSP TransferSuspensionMessage (POST /transfers/{id}/suspension)
            CUT->>TCK: DataFlowSuspendMessage (POST /dataflows/{processId}/suspend)
            TCK-->>CUT: 200 OK
            TCK->>CUT: DSP TransferStartMessage (POST /transfers/{id}/start)
            CUT->>TCK: DataFlowResumeMessage (POST /dataflows/{processId}/resume)
            TCK-->>CUT: 200 OK
            TCK->>CUT: DSP TransferCompletionMessage (POST /transfers/{id}/completion)
            CUT->>TCK: Completed notification (POST /dataflows/{processId}/completed)
            """)
    public void cp_p_02_01() {
        signalingPipeline
                .expectDataFlowStartMessage()
                .sendTransferRequestMessage(agreementId, "HttpData-PULL")
                .thenWaitForDataFlowStartMessage()
                .expectDataFlowSuspendMessage(processId)
                .sendTransferSuspensionMessage(processId)
                .thenWaitForSuspendMessage()
                .expectDataFlowResumeMessage(processId)
                .sendTransferStartMessage(processId)
                .thenWaitForResumeMessage()
                .expectDataFlowCompletedMessage(processId)
                .sendTransferCompletionMessage(processId)
                .thenWaitForCompletedMessage()
                .execute();
    }

    @MandatoryTest
    @DisplayName("CP_P:02-02: Verify DataFlowSuspendMessage is dispatched when transfer is suspended and terminate notification is sent")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (consumer CP + provider data plane)
            participant CUT as Provider Control-Plane Under Test

            TCK->>CUT: DSP TransferRequestMessage (POST /transfers/request)
            CUT->>TCK: DataFlowStartMessage (POST /dataflows/start)
            TCK-->>CUT: 200 OK + DataFlowStatusMessage (state=STARTED)
            TCK->>CUT: DSP TransferSuspensionMessage (POST /transfers/{id}/suspension)
            CUT->>TCK: DataFlowSuspendMessage (POST /dataflows/{processId}/suspend)
            TCK-->>CUT: 200 OK
            TCK->>CUT: DSP TransferTerminationMessage (POST /transfers/{id}/termination)
            CUT->>TCK: Terminate notification (POST /dataflows/{processId}/terminate)
            """)
    public void cp_p_02_02() {
        signalingPipeline
                .expectDataFlowStartMessage()
                .sendTransferRequestMessage(agreementId, "HttpData-PULL")
                .thenWaitForDataFlowStartMessage()
                .expectDataFlowSuspendMessage(processId)
                .sendTransferSuspensionMessage(processId)
                .thenWaitForSuspendMessage()
                .expectDataFlowTerminateMessage(processId)
                .sendTransferTerminationMessage(processId)
                .thenWaitForTerminateMessage()
                .execute();
    }
}
