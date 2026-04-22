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
import org.eclipse.dataspacetck.dps.system.pipeline.ControlPlaneSignalingPipelineImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("base-compliance")
@DisplayName("CP_P: Control plane provider signaling scenarios")
public class ControlPlaneProviderSignalingTest extends AbstractVerificationTest {

    @Inject
    protected ControlPlaneSignalingPipeline signalingPipeline;

    @ConfigParam
    protected String processId = randomUUID().toString();

    @ConfigParam
    protected String agreementId = randomUUID().toString();

    @ConfigParam
    protected String datasetId = randomUUID().toString();

    @MandatoryTest
    @DisplayName("CP_P:01-01: Verify DataFlowStartMessage is dispatched when a transfer request is received and completed notification is sent to the data plane")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (consumer CP + data plane)
            participant CUT as Control-Plane Under Test (provider CP)

            TCK->>CUT: DSP TransferRequestMessage (POST /transfers/request)
            CUT->>TCK: DataFlowStartMessage (POST /dataflows/start)
            TCK-->>CUT: 200 OK + DataFlowStatusMessage (state=STARTED)
            TCK->>CUT: DSP TransferCompletionMessage (POST /transfers/{id}/completion)
            """)
    public void cp_p_01_01() {
        signalingPipeline
                .expectDataFlowStartMessage()
                .sendTransferRequestMessage(agreementId, "HttpData-PULL")
                .thenWaitForDataFlowStartMessage()
                .sendTransferCompletionMessage(processId)
                .execute();

        var received = ((ControlPlaneSignalingPipelineImpl) signalingPipeline).getReceivedStartMessage();

        assertThat(received).isNotNull();
        assertThat(received).containsKey("messageId");
        assertThat(received).containsKey("participantId");
        assertThat(received).containsKey("counterPartyId");
        assertThat(received).containsKey("dataspaceContext");
        assertThat(received).containsKey("processId");
        assertThat(received).containsKey("agreementId");
        assertThat(received).containsKey("transferType");
        assertThat(received).containsKey("claims");
    }

    @MandatoryTest
    @DisplayName("CP_P:01-02: Verify DataFlowStartMessage is dispatched when a transfer request is received and terminate notification is sent to the data plane")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (consumer CP + data plane)
            participant CUT as Control-Plane Under Test (provider CP)

            TCK->>CUT: DSP TransferRequestMessage (POST /transfers/request)
            CUT->>TCK: DataFlowStartMessage (POST /dataflows/start)
            TCK-->>CUT: 200 OK + DataFlowStatusMessage (state=STARTED)
            TCK->>CUT: DSP TransferTerminationMessage (POST /transfers/{id}/termination)
            """)
    public void cp_p_01_02() {
        signalingPipeline
                .expectDataFlowStartMessage()
                .sendTransferRequestMessage(agreementId, "HttpData-PULL")
                .thenWaitForDataFlowStartMessage()
                .sendTransferTerminationMessage(processId)
                .execute();

        var received = ((ControlPlaneSignalingPipelineImpl) signalingPipeline).getReceivedStartMessage();

        assertThat(received).isNotNull();
        assertThat(received).containsKey("messageId");
        assertThat(received).containsKey("participantId");
        assertThat(received).containsKey("counterPartyId");
        assertThat(received).containsKey("dataspaceContext");
        assertThat(received).containsKey("processId");
        assertThat(received).containsKey("agreementId");
        assertThat(received).containsKey("transferType");
        assertThat(received).containsKey("claims");
    }
}
