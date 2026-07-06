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
 * Verifies compliance of a consumer data plane with the HTTP transfer profile for PULL flows.
 *
 * <p>The TCK acts as the <em>consumer control plane</em> and validates that the consumer data plane
 * under test accepts an HTTP pull profile {@code DataAddress} delivered in the started notification
 * (in pull flows the provider generates the address and it is conveyed to the consumer). The delivered
 * address always carries the Token Renewal profile properties, so the consumer is exercised with a
 * renewable address.
 */
@Tag("http-profile")
@DisplayName("DP_C_HTTP_PULL: Data plane consumer HTTP profile pull flows")
@ExtendWith(SystemBootstrapExtension.class)
public class DataPlaneConsumerSignalingHttpPullTest {

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
    @DisplayName("DP_C_HTTP_PULL:01-01: Verify the consumer accepts a renewable http-pull DataAddress in the started notification")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (consumer control plane)
            participant CUT as Consumer Data-Plane Under Test
            
            TCK->>CUT: DataFlowPrepareMessage (POST /dataflows/prepare)
            CUT-->>TCK: 200 OK + DataFlowStatusMessage (state=PREPARED) with null DataAddress
            TCK->>CUT: DataFlowStartedNotificationMessage + renewable http-pull DataAddress (refreshToken, expiresIn, refreshEndpoint) (POST /dataflows/{id}/started)
            CUT-->>TCK: 200 OK
            """)
    public void dp_c_http_pull_01_01() {
        signalingPipeline
                .sendDataFlowPrepareMessage(agreementId, datasetId, profile)
                .expectReceivedDataAddressToBeNull()
                .sendDataFlowStartedNotificationWithRenewableHttpPullDataAddress()
                .thenWaitForDataFlowToBeInState("STARTED")
                .execute();
    }

    @MandatoryTest
    @Tag("sync")
    @DisplayName("DP_C_HTTP_PULL:02-01: Verify the consumer rejects an http-pull DataAddress that lacks Token Renewal properties")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (consumer control plane)
            participant CUT as Consumer Data-Plane Under Test
            
            TCK->>CUT: DataFlowPrepareMessage (POST /dataflows/prepare)
            CUT-->>TCK: 200 OK + DataFlowStatusMessage (state=PREPARED) with null DataAddress
            TCK->>CUT: DataFlowStartedNotificationMessage + http-pull DataAddress WITHOUT renewal properties (POST /dataflows/{id}/started)
            CUT-->>TCK: rejected (non-2xx, no STARTED transition)
            """)
    public void dp_c_http_pull_02_01() {
        signalingPipeline
                .sendDataFlowPrepareMessage(agreementId, datasetId, profile)
                .expectReceivedDataAddressToBeNull()
                .sendDataFlowStartedNotificationWithNonRenewableHttpPullDataAddressExpectingRejection()
                .execute();
    }
}
