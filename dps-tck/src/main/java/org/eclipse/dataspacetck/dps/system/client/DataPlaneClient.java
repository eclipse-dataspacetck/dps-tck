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

package org.eclipse.dataspacetck.dps.system.client;

import java.util.Map;

/**
 * Client for sending DPS messages to the data plane under test.
 */
public interface DataPlaneClient {

    DataFlowResult prepare(boolean async, String processId, String agreementId, String datasetId, String profile);

    DataFlowResult start(boolean async, String processId, String agreementId, String datasetId, String profile);

    DataFlowResult startWithDataAddress(boolean async, String processId, String agreementId, String datasetId, String profile, Map<String, Object> dataAddress);

    void sendStarted(String dataFlowId);

    void sendStarted(String dataFlowId, Map<String, Object> dataAddress);

    /**
     * Sends a started notification carrying the given DataAddress and returns {@code true} iff the
     * consumer rejected it (non-2xx response / no STARTED transition). Used by negative tests.
     */
    boolean startedNotificationRejected(String dataFlowId, Map<String, Object> dataAddress);

    /**
     * Requests a new access token from the provider authorization server, acting as the data client
     * per the Token Renewal profile. The {@code accessToken} (the DataAddress {@code authorization} value)
     * is carried in the client-authentication JWT's {@code token} claim. When {@code validClientAuthentication}
     * is {@code false}, a client-authentication JWT with deliberately wrong {@code iss}/{@code sub}/{@code aud}
     * is presented so negative tests can verify the provider rejects it.
     */
    TokenRefreshResult refreshToken(String refreshEndpoint, String refreshToken, String accessToken, boolean validClientAuthentication);

    void sendSuspend(String dataFlowId);

    DataFlowResult resume(String dataFlowId);

    void sendTerminate(String dataFlowId);

    void sendCompleted(String dataFlowId);

    void sendCompletedCallback(String processId, String dataFlowId);

    DataFlowStatusResponseMessage getStatus(String dataFlowId);

    record DataFlowResult(String dataFlowId, String state, Map<String, Object> dataAddress) {}

    /**
     * Outcome of a token-renewal request. {@code rejected} is true when the provider did not issue a
     * new access token (non-2xx / OAuth error); {@code tokenResponse} holds the parsed token response
     * on success (null when rejected).
     */
    record TokenRefreshResult(boolean rejected, Map<String, Object> tokenResponse) {}

    record DataFlowStatusResponseMessage(String dataFlowId, String state) {
    }
}
