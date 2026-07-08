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

import org.eclipse.dataspacetck.core.api.system.CallbackEndpoint;
import org.eclipse.dataspacetck.dps.system.client.DataPlaneClient;
import org.eclipse.dataspacetck.dps.system.connector.LocalDataPlaneConnector;
import org.eclipse.dataspacetck.dps.system.crypto.RefreshTokenAuthenticator;

import java.util.Map;

/**
 * In-process implementation of {@link DataPlaneClient} backed by a {@link LocalDataPlaneConnector}.
 */
public class LocalDataPlaneClient implements DataPlaneClient {

    private final LocalDataPlaneConnector connector;
    private final CallbackEndpoint callbackEndpoint;
    private final RefreshTokenAuthenticator refreshTokenAuthenticator;

    public LocalDataPlaneClient(LocalDataPlaneConnector connector, CallbackEndpoint callbackEndpoint, RefreshTokenAuthenticator refreshTokenAuthenticator) {
        this.connector = connector;
        this.callbackEndpoint = callbackEndpoint;
        this.refreshTokenAuthenticator = refreshTokenAuthenticator;
    }

    @Override
    public DataFlowResult prepare(boolean async, String processId, String agreementId, String datasetId, String profile) {
        return connector.handlePrepare(callbackEndpoint.getAddress(), processId, async, profile);
    }

    @Override
    public DataFlowResult start(boolean async, String processId, String agreementId, String datasetId, String profile) {
        return connector.handleStart(callbackEndpoint.getAddress(), processId, async, profile);
    }

    @Override
    public DataFlowResult startWithDataAddress(boolean async, String processId, String agreementId, String datasetId, String profile, Map<String, Object> dataAddress) {
        return connector.handleStart(callbackEndpoint.getAddress(), processId, async, profile);
    }

    @Override
    public void sendStarted(String dataFlowId) {
        connector.handleStarted(dataFlowId);
    }

    @Override
    public void sendStarted(String dataFlowId, Map<String, Object> dataAddress) {
        connector.handleStarted(dataFlowId, dataAddress);
    }

    @Override
    public boolean startedNotificationRejected(String dataFlowId, Map<String, Object> dataAddress) {
        return connector.handleStartedRejectingNonRenewable(dataFlowId, dataAddress);
    }

    @Override
    public TokenRefreshResult refreshToken(String refreshEndpoint, String refreshToken, String accessToken, boolean validClientAuthentication) {
        var clientAuthToken = validClientAuthentication
                ? refreshTokenAuthenticator.createBearerToken(accessToken)
                : refreshTokenAuthenticator.createInvalidBearerToken(accessToken);
        return connector.handleRefresh(clientAuthToken, refreshToken);
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
    public void sendCompletedCallback(String processId, String dataFlowId) {
        connector.sendCompletedCallback(callbackEndpoint.getAddress(), processId, dataFlowId);
    }

    @Override
    public DataFlowStatusResponseMessage getStatus(String dataFlowId) {
        return new DataFlowStatusResponseMessage(dataFlowId, connector.getState(dataFlowId));
    }
}
