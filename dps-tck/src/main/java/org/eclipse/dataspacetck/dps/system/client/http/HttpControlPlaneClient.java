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

package org.eclipse.dataspacetck.dps.system.client.http;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.eclipse.dataspacetck.core.spi.boot.Monitor;
import org.eclipse.dataspacetck.dps.system.client.ControlPlaneClient;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;

/**
 * HTTP client that signals the control plane under test to trigger a data flow preparation.
 */
public class HttpControlPlaneClient implements ControlPlaneClient {

    private static final MediaType JSON = MediaType.get("application/json");

    private final String webhookUrl;
    private final String signalingUrl;
    private final Monitor monitor;
    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;

    public HttpControlPlaneClient(String webhookUrl, String signalingUrl, Monitor monitor, ObjectMapper mapper) {
        this.webhookUrl = webhookUrl;
        this.signalingUrl = signalingUrl;
        this.monitor = monitor;
        this.httpClient = new OkHttpClient();
        this.mapper = mapper;
    }

    @Override
    public String triggerDataFlowPreparation(String agreementId, String datasetId, String dataPlaneUrl) {
        return triggerInternal(agreementId, datasetId, dataPlaneUrl, false);
    }

    @Override
    public String triggerDataFlowPreparationAsync(String agreementId, String datasetId, String dataPlaneUrl) {
        return triggerInternal(agreementId, datasetId, dataPlaneUrl, true);
    }

    @Override
    public void notifyPrepared(String processId, String dataFlowId) {
        sendStatusCallback(signalingUrl + "/transfers/" + processId + "/dataflow/prepared", dataFlowId, "PREPARED");
    }

    @Override
    public void notifyStarted(String processId, String dataFlowId) {
        sendStatusCallback(signalingUrl + "/transfers/" + processId + "/dataflow/started", dataFlowId, "STARTED");
    }

    private String triggerInternal(String agreementId, String datasetId, String dataPlaneUrl, boolean async) {
        try {
            var url = webhookUrl + "/dataflows/trigger";
            var body = mapper.writeValueAsString(Map.of(
                    "agreementId", agreementId,
                    "datasetId", datasetId,
                    "dataPlaneUrl", dataPlaneUrl,
                    "dspUrl", dataPlaneUrl, // TODO: refactor
                    "async", async
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
                var responseBody = response.body().string();
                var responseJson = mapper.readValue(responseBody, Map.class);
                var processId = (String) responseJson.get("id");
                if (processId == null) {
                    throw new RuntimeException("Control plane response missing 'id' field");
                }
                return processId;
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to trigger data flow preparation", e);
        }
    }

    private void sendStatusCallback(String url, String dataFlowId, String state) {
        try {
            var body = mapper.writeValueAsString(Map.of(
                    "dataFlowId", dataFlowId,
                    "state", state
            ));
            var request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(body, JSON))
                    .build();
            monitor.debug("Sending " + state + " callback to " + url);
            try (var response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("Failed to send " + state + " callback: HTTP " + response.code());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to send status callback", e);
        }
    }

}
