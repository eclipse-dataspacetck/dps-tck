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

package org.eclipse.dataspacetck.dps.system.client.local;

import org.eclipse.dataspacetck.dps.system.client.ControlPlaneClient;
import org.eclipse.dataspacetck.dps.system.connector.LocalControlPlaneConnector;

/**
 * In-process implementation of {@link ControlPlaneClient} backed by a {@link LocalControlPlaneConnector}.
 */
public class LocalControlPlaneClient implements ControlPlaneClient {

    private final LocalControlPlaneConnector connector;

    public LocalControlPlaneClient(LocalControlPlaneConnector connector) {
        this.connector = connector;
    }

    @Override
    public String triggerDataFlowPreparation(String agreementId, String datasetId, String dataPlaneUrl) {
        return connector.triggerDataFlowPreparation(agreementId, datasetId, dataPlaneUrl);
    }

    @Override
    public String triggerDataFlowPreparationAsync(String agreementId, String datasetId, String dataPlaneUrl) {
        return connector.triggerDataFlowPreparationAsync(agreementId, datasetId, dataPlaneUrl);
    }

    @Override
    public void notifyPrepared(String processId, String dataFlowId) {
        connector.receivePreparedCallback(processId, dataFlowId);
    }

    @Override
    public void notifyStarted(String processId, String dataFlowId) {
        connector.receiveStartedCallback(processId, dataFlowId);
    }
}
