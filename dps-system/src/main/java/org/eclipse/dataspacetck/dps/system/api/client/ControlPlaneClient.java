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

package org.eclipse.dataspacetck.dps.system.api.client;

/**
 * Client for signaling the control plane under test to trigger data flow operations.
 */
public interface ControlPlaneClient {

    /**
     * Sends a signal to the control plane to trigger a data flow preparation
     * for the given transfer process.
     *
     * @param processId    the transfer process ID
     * @param agreementId  the contract agreement ID
     * @param datasetId    the dataset ID
     * @param dataPlaneUrl the URL of the data plane (TCK) that will receive the DataFlowPrepareMessage
     */
    void triggerDataFlowPreparation(String processId, String agreementId, String datasetId, String dataPlaneUrl);

    /**
     * Signals the control plane to transition the transfer process to completing state,
     * causing it to send a completed notification to the data plane.
     *
     * @param processId the transfer process ID
     */
    void signalDataFlowCompleted(String processId);

}
