/*
 *  Copyright (c) 2026 Metaform Systems, Inc.
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Metaform Systems, Inc. - initial API and implementation
 *
 */

package org.eclipse.dataspacetck.dps.verification.dataplane.http.pull;

import org.eclipse.dataspacetck.api.system.MandatoryTest;
import org.eclipse.dataspacetck.api.system.TestSequenceDiagram;
import org.eclipse.dataspacetck.core.api.system.ConfigParam;
import org.eclipse.dataspacetck.core.api.system.Inject;
import org.eclipse.dataspacetck.core.system.SystemBootstrapExtension;
import org.eclipse.dataspacetck.dps.system.pipeline.DataPlaneSignalingPipeline;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

import static java.util.UUID.randomUUID;

/**
 * Verifies compliance of a provider data plane with the HTTP transfer profile for PULL flows.
 *
 * <p>The TCK acts as the <em>provider control plane</em> and validates that the provider data plane
 * under test returns, in the start response, a {@code DataAddress} that conforms to the HTTP pull
 * profile format. It also verifies the Token Renewal profile: that the {@code DataAddress} carries
 * valid renewal properties and that the provider authorization server honors a refresh request.
 */
@Tag("http-profile")
@DisplayName("DP_P_HTTP_PULL: Data plane provider HTTP profile pull flows")
@ExtendWith(SystemBootstrapExtension.class)
public class DataPlaneProviderSignalingHttpPullTest {

    @Inject
    protected DataPlaneSignalingPipeline signalingPipeline;

    @ConfigParam
    protected String agreementId = randomUUID().toString();

    @ConfigParam
    protected String datasetId = randomUUID().toString();

    @ConfigParam
    protected String profile = "https://w3id.org/dspace-sig/profile/http-pull";

    @MandatoryTest
    @Tag("sync")
    @DisplayName("DP_P_HTTP_PULL:01-01: Verify the start response DataAddress conforms to the HTTP pull and Token Renewal profiles and the provider honors a refresh_token request")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (provider control plane / data client)
            participant CUT as Provider Data-Plane Under Test
            
            TCK->>CUT: DataFlowStartMessage (POST /dataflows/start)
            CUT-->>TCK: 200 OK + DataFlowStatusMessage (state=STARTED) with renewable http-pull DataAddress
            Note over TCK: assert endpointType=http-pull, HTTPS endpoint, authType=bearer, authorization, refreshToken, numeric expiresIn, refreshEndpoint
            TCK->>CUT: POST refreshEndpoint (grant_type=refresh_token, refresh_token, Authorization: Bearer)
            CUT-->>TCK: 200 OK + OAuth token response (access_token, token_type)
            """)
    public void dp_p_http_pull_01_01() {
        signalingPipeline
                .sendDataFlowStartMessage(agreementId, datasetId, profile)
                .expectReceivedDataAddressToBeValidHttpPull()
                .expectReceivedDataAddressToHaveValidRenewalProperties()
                .thenWaitForDataFlowToBeInState("STARTED")
                .thenDriveTokenRenewal()
                .execute();
    }

    @MandatoryTest
    @Tag("sync")
    @DisplayName("DP_P_HTTP_PULL:02-01: Verify the provider rejects a token refresh presenting an invalid refresh token")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (provider control plane / data client)
            participant CUT as Provider Data-Plane Under Test
            
            TCK->>CUT: DataFlowStartMessage (POST /dataflows/start)
            CUT-->>TCK: 200 OK + DataFlowStatusMessage (state=STARTED) with renewable http-pull DataAddress
            TCK->>CUT: POST refreshEndpoint (grant_type=refresh_token, INVALID refresh_token, valid Authorization: Bearer)
            CUT-->>TCK: rejected (non-2xx / OAuth error, no access token issued)
            """)
    public void dp_p_http_pull_02_01() {
        signalingPipeline
                .sendDataFlowStartMessage(agreementId, datasetId, profile)
                .expectReceivedDataAddressToHaveValidRenewalProperties()
                .thenWaitForDataFlowToBeInState("STARTED")
                .thenDriveTokenRenewalWithInvalidRefreshTokenExpectingRejection()
                .execute();
    }

    @MandatoryTest
    @Tag("sync")
    @DisplayName("DP_P_HTTP_PULL:02-02: Verify the provider rejects a token refresh with an invalid client-authentication token (wrong aud)")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (provider control plane / data client)
            participant CUT as Provider Data-Plane Under Test
            
            TCK->>CUT: DataFlowStartMessage (POST /dataflows/start)
            CUT-->>TCK: 200 OK + DataFlowStatusMessage (state=STARTED) with renewable http-pull DataAddress
            TCK->>CUT: POST refreshEndpoint (grant_type=refresh_token, valid refresh_token, Authorization: Bearer with wrong aud)
            CUT-->>TCK: rejected (non-2xx / OAuth error, no access token issued)
            """)
    public void dp_p_http_pull_02_02() {
        signalingPipeline
                .sendDataFlowStartMessage(agreementId, datasetId, profile)
                .expectReceivedDataAddressToHaveValidRenewalProperties()
                .thenWaitForDataFlowToBeInState("STARTED")
                .thenDriveTokenRenewalWithInvalidClientAuthenticationExpectingRejection()
                .execute();
    }
}
