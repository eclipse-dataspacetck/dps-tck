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

import org.eclipse.dataspacetck.dps.system.client.DataPlaneClient;
import org.eclipse.dataspacetck.dps.system.connector.LocalDataPlaneConnector;

import java.util.Map;

/**
 * In-process implementation of {@link DataPlaneClient} backed by a {@link LocalDataPlaneConnector}.
 */
public class LocalDataPlaneClient implements DataPlaneClient {

    private final LocalDataPlaneConnector connector;

    public LocalDataPlaneClient(LocalDataPlaneConnector connector) {
        this.connector = connector;
    }

    @Override
    public DataFlowResult prepare(boolean async, String callbackAddress, String processId, String agreementId, String datasetId, String transferType) {
        return connector.handlePrepare(callbackAddress, processId, async, transferType);
    }

    @Override
    public DataFlowResult start(boolean async, String callbackAddress, String processId, String agreementId, String datasetId, String transferType) {
        return connector.handleStart(callbackAddress, processId, async, transferType);
    }

    @Override
    public DataFlowResult startWithDataAddress(boolean async, String callbackAddress, String processId, String agreementId, String datasetId, String transferType, Map<String, Object> dataAddress) {
        return connector.handleStart(callbackAddress, processId, async, transferType);
    }

    @Override
    public void sendStarted(String dataFlowId) {
        connector.handleStarted(dataFlowId);
    }

    @Override
    public void sendSuspend(String dataFlowId) {
        connector.handleSuspend(dataFlowId);
    }

    @Override
    public DataFlowResult resume(String dataFlowId) {
        return connector.handleResume(dataFlowId);
    }

    @Override
    public void sendTerminate(String dataFlowId) {
        connector.handleTerminate(dataFlowId);
    }

    @Override
    public void sendCompleted(String dataFlowId) {
        connector.handleCompleted(dataFlowId);
    }

    @Override
    public void sendCompletedCallback(String callbackAddress, String processId, String dataFlowId) {
        connector.sendCompletedCallback(callbackAddress, processId, dataFlowId);
    }

    @Override
    public DataFlowStatusResponseMessage getStatus(String dataFlowId) {
        return new DataFlowStatusResponseMessage(dataFlowId, connector.getState(dataFlowId));
    }
}
