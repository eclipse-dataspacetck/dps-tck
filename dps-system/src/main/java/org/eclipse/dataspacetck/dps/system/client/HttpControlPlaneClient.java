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

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.eclipse.dataspacetck.core.spi.boot.Monitor;
import org.eclipse.dataspacetck.dps.system.api.client.ControlPlaneClient;

import java.io.IOException;
import java.util.Map;

/**
 * HTTP client that signals the control plane under test to trigger a data flow preparation.
 */
public class HttpControlPlaneClient implements ControlPlaneClient {

    private static final MediaType JSON = MediaType.get("application/json");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String webhookUrl;
    private final Monitor monitor;
    private final OkHttpClient httpClient;

    public HttpControlPlaneClient(String webhookUrl, Monitor monitor) {
        this.webhookUrl = webhookUrl;
        this.monitor = monitor;
        this.httpClient = new OkHttpClient();
    }

    @Override
    public void triggerDataFlowPreparation(String processId, String agreementId, String datasetId, String dataPlaneUrl) {
        try {
            var url = webhookUrl + "/dataflows/trigger";
            var body = MAPPER.writeValueAsString(Map.of(
                    "processId", processId,
                    "agreementId", agreementId,
                    "datasetId", datasetId,
                    "dataPlaneUrl", dataPlaneUrl,
                    "dspUrl", dataPlaneUrl // TODO: refactor
            ));
            var request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(body, JSON))
                    .build();

            monitor.debug("Triggering data flow preparation at " + url);
            try (var response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("Failed to trigger data flow preparation: HTTP " + response.code());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to trigger data flow preparation", e);
        }
    }

}
