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

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.eclipse.dataspacetck.core.spi.boot.Monitor;
import org.eclipse.dataspacetck.dps.system.client.DataPlaneClient;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP client that sends DPS messages to the data plane under test.
 */
public class HttpDataPlaneClient implements DataPlaneClient {

    private static final MediaType JSON = MediaType.get("application/json");

    private final String dataPlaneUrl;
    private final Monitor monitor;
    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;

    public HttpDataPlaneClient(String dataPlaneUrl, Monitor monitor, ObjectMapper mapper) {
        this.dataPlaneUrl = dataPlaneUrl;
        this.monitor = monitor;
        this.httpClient = new OkHttpClient();
        this.mapper = mapper;
    }

    @Override
    public DataFlowResult prepare(boolean async, String callbackAddress, String processId, String agreementId, String datasetId, String transferType) {
        try {
            var body = mapper.writeValueAsString(Map.of(
                    "messageId", UUID.randomUUID().toString(),
                    "participantId", "tck-participant",
                    "counterPartyId", "tck-counterparty",
                    "dataspaceContext", "tck-dataspace",
                    "processId", processId,
                    "agreementId", agreementId,
                    "datasetId", datasetId,
                    "callbackAddress", callbackAddress,
                    "transferType", transferType,
                    "claims", Map.of()
            ));
            monitor.debug("HTTP DP: sending DataFlowPrepareMessage for processId=" + processId);
            var response = post(dataPlaneUrl + "/dataflows/prepare", body);
            return parseDataFlowResult(response);
        } catch (IOException e) {
            throw new RuntimeException("Failed to send DataFlowPrepareMessage", e);
        }
    }

    @Override
    public DataFlowResult start(boolean async, String callbackAddress, String processId, String agreementId, String datasetId, String transferType) {
        try {
            var body = mapper.writeValueAsString(Map.of(
                    "messageId", UUID.randomUUID().toString(),
                    "participantId", "tck-participant",
                    "counterPartyId", "tck-counterparty",
                    "dataspaceContext", "tck-dataspace",
                    "processId", processId,
                    "agreementId", agreementId,
                    "datasetId", datasetId,
                    "callbackAddress", callbackAddress,
                    "transferType", transferType,
                    "claims", Map.of()
            ));
            monitor.debug("HTTP DP: sending DataFlowStartMessage for processId=" + processId);
            var response = post(dataPlaneUrl + "/dataflows/start", body);
            return parseDataFlowResult(response);
        } catch (IOException e) {
            throw new RuntimeException("Failed to send DataFlowStartMessage", e);
        }
    }

    @Override
    public void sendStarted(String dataFlowId) {
        try {
            var body = mapper.writeValueAsString(Map.of("messageId", UUID.randomUUID().toString()));
            monitor.debug("HTTP DP: sending started notification for dataFlowId=" + dataFlowId);
            post(dataPlaneUrl + "/dataflows/" + dataFlowId + "/started", body);
        } catch (IOException e) {
            throw new RuntimeException("Failed to send started notification", e);
        }
    }

    @Override
    public void sendSuspend(String dataFlowId) {
        try {
            var body = mapper.writeValueAsString(Map.of("messageId", UUID.randomUUID().toString()));
            monitor.debug("HTTP DP: sending suspend for dataFlowId=" + dataFlowId);
            post(dataPlaneUrl + "/dataflows/" + dataFlowId + "/suspend", body);
        } catch (IOException e) {
            throw new RuntimeException("Failed to send DataFlowSuspendMessage", e);
        }
    }

    @Override
    public DataFlowResult resume(String dataFlowId) {
        try {
            var body = mapper.writeValueAsString(Map.of("messageId", UUID.randomUUID().toString()));
            monitor.debug("HTTP DP: sending resume for dataFlowId=" + dataFlowId);
            var response = post(dataPlaneUrl + "/dataflows/" + dataFlowId + "/resume", body);
            return parseDataFlowResult(response);
        } catch (IOException e) {
            throw new RuntimeException("Failed to send DataFlowResumeMessage", e);
        }
    }

    @Override
    public void sendTerminate(String dataFlowId) {
        try {
            var body = mapper.writeValueAsString(Map.of("messageId", UUID.randomUUID().toString()));
            monitor.debug("HTTP DP: sending terminate for dataFlowId=" + dataFlowId);
            post(dataPlaneUrl + "/dataflows/" + dataFlowId + "/terminate", body);
        } catch (IOException e) {
            throw new RuntimeException("Failed to send DataFlowTerminateMessage", e);
        }
    }

    @Override
    public void sendCompleted(String dataFlowId) {
        try {
            var body = mapper.writeValueAsString(Map.of());
            monitor.debug("HTTP DP: sending completed notification for dataFlowId=" + dataFlowId);
            post(dataPlaneUrl + "/dataflows/" + dataFlowId + "/completed", body);
        } catch (IOException e) {
            throw new RuntimeException("Failed to send completed notification", e);
        }
    }

    @Override
    public void sendCompletedCallback(String callbackAddress, String processId, String dataFlowId) {
        // the real data plane sends this callback autonomously; nothing to trigger over HTTP
    }

    private String post(String url, String body) throws IOException {
        var request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body, JSON))
                .build();
        try (var response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("HTTP " + response.code() + " from " + url);
            }
            return response.body().string();
        }
    }

    @SuppressWarnings("unchecked")
    private DataFlowResult parseDataFlowResult(String responseBody) throws IOException {
        var responseMap = mapper.readValue(responseBody, Map.class);
        var dataFlowId = (String) responseMap.get("dataFlowId");
        var state = (String) responseMap.get("state");
        return new DataFlowResult(dataFlowId, state);
    }
}
