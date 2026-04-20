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
     * Waits until the TCK data plane has received the DataFlowPrepareMessage.
     */
    ControlPlaneSignalingPipeline thenWaitForPrepareMessage();

    /**
     * Registers a handler on the TCK data plane endpoint for the completed notification
     * ({@code POST /dataflows/{processId}/completed}) that the control plane sends when
     * the transfer process completes.
     */
    ControlPlaneSignalingPipeline expectDataFlowCompletedMessage(String processId);

    /**
     * Signals the control plane under test to complete the data flow for the given process ID
     * by calling the trigger controller's complete endpoint.
     */
    ControlPlaneSignalingPipeline signalDataFlowCompleted(String processId);

    /**
     * Waits until the TCK data plane has received the completed notification.
     */
    ControlPlaneSignalingPipeline thenWaitForCompletedMessage();

}
