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

package org.eclipse.dataspacetck.dps.system.api.pipeline;

import org.eclipse.dataspacetck.core.api.pipeline.AsyncPipeline;

/**
 * Pipeline for verifying the control plane consumer signaling behavior.
 * The TCK acts as the data plane and verifies that the control plane under test
 * correctly dispatches a {@code DataFlowPrepareMessage} when triggered.
 */
public interface ControlPlaneSignalingPipeline extends AsyncPipeline<ControlPlaneSignalingPipeline> {

    /**
     * Signals the control plane under test to initiate a data flow preparation.
     */
    ControlPlaneSignalingPipeline triggerDataFlowPreparation(String processId, String agreementId, String datasetId);

    /**
     * Registers a handler on the TCK data plane endpoint and waits for the
     * {@code DataFlowPrepareMessage} to arrive. Validates the message and responds 200.
     * The received message is stored for subsequent verification.
     */
    ControlPlaneSignalingPipeline expectDataFlowPrepareMessage();

    /**
     * Registers a handler on the TCK data plane endpoint for the completed notification
     * ({@code POST /dataflows/{processId}/completed}) that the control plane sends when
     * the transfer process completes.
     */
    ControlPlaneSignalingPipeline expectDataFlowCompletedMessage(String processId);

    ControlPlaneSignalingPipeline expectDataFlowStartMessage();

    /**
     * Registers a handler on the TCK data plane endpoint for the terminate message
     * ({@code POST /dataflows/{processId}/terminate}) that the control plane sends when
     * the transfer process completes.
     */
    ControlPlaneSignalingPipeline expectDataFlowTerminateMessage(String processId);

    /**
     * Registers a handler on the TCK data plane endpoint for the suspend message
     * ({@code POST /dataflows/{processId}/suspend}) that the control plane sends when
     * the transfer process is suspended.
     */
    ControlPlaneSignalingPipeline expectDataFlowSuspendMessage(String processId);

    /**
     * Registers a handler on the TCK data plane endpoint for the resume message
     * ({@code POST /dataflows/{processId}/resume}) that the control plane sends when
     * the transfer process is resumed.
     */
    ControlPlaneSignalingPipeline expectDataFlowResumeMessage(String processId);

    /**
     * Waits until the TCK data plane has received the DataFlowPrepareMessage.
     */
    ControlPlaneSignalingPipeline thenWaitForPrepareMessage();

    ControlPlaneSignalingPipeline thenWaitForDataFlowStartMessage();


    /**
     * Waits until the TCK data plane has received the completed notification.
     */
    ControlPlaneSignalingPipeline thenWaitForCompletedMessage();

    /**
     * Waits until the TCK data plane has received the terminate message.
     */
    ControlPlaneSignalingPipeline thenWaitForTerminateMessage();

    ControlPlaneSignalingPipeline thenWaitForTransferRequestMessage();

    ControlPlaneSignalingPipeline thenWaitForTransferToBeInState(String state);

    ControlPlaneSignalingPipeline sendTransferRequestMessage(String agreementId, String transferType);

    ControlPlaneSignalingPipeline sendTransferStartMessage(String processId);

    ControlPlaneSignalingPipeline sendTransferCompletionMessage(String processId);

    ControlPlaneSignalingPipeline sendTransferTerminationMessage(String processId);

    /**
     * Waits until the TCK data plane has received the suspend message.
     */
    ControlPlaneSignalingPipeline thenWaitForSuspendMessage();

    /**
     * Waits until the TCK data plane has received the resume message.
     */
    ControlPlaneSignalingPipeline thenWaitForResumeMessage();

    ControlPlaneSignalingPipeline sendTransferSuspensionMessage(String processId);

    ControlPlaneSignalingPipeline sendTransferResumptionMessage(String processId);

}
