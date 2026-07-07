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
import org.eclipse.dataspacetck.dps.system.client.DspClient;
import org.jspecify.annotations.NonNull;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public class HttpDspClient implements DspClient {

    private static final MediaType JSON = MediaType.get("application/json");
    private final String initialDspUrl;
    private final Monitor monitor;
    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;

    public HttpDspClient(String protocolUrl, Monitor monitor, ObjectMapper mapper) {
        this.initialDspUrl = protocolUrl;
        this.monitor = monitor;
        this.mapper = mapper;
        this.httpClient = new OkHttpClient();
    }

    @Override
    public String dspTransferState(String senderId, String callbackAddress, String processId) {
        try {
            var url = callbackAddress + "/transfers/" + processId;

            var request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", createAuthorizationToken(senderId))
                    .get()
                    .build();

            return execute(request).get("state").toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to signal data flow completion", e);
        }
    }

    @Override
    public void sendTransferStartMessage(String senderId, String callbackAddress, String processId) {
        var requestBody = message(processId, "TransferStartMessage");
        send(callbackAddress + "/transfers/" + processId + "/start", requestBody, senderId);
    }

    @Override
    public void sendTransferCompletionMessage(String senderId, String callbackAddress, String processId) {
        var requestBody = message(processId, "TransferCompletionMessage");
        send(callbackAddress + "/transfers/" + processId + "/completion", requestBody, senderId);
    }

    @Override
    public void sendTransferTerminationMessage(String senderId, String callbackAddress, String processId) {
        var requestBody = message(processId, "TransferTerminationMessage");
        send(callbackAddress + "/transfers/" + processId + "/termination", requestBody, senderId);
    }

    @Override
    public void sendTransferSuspensionMessage(String senderId, String callbackAddress, String processId) {
        var requestBody = message(processId, "TransferSuspensionMessage");
        send(callbackAddress + "/transfers/" + processId + "/suspension", requestBody, senderId);
    }

    @Override
    public TransferRequestResult sendTransferRequestMessage(String senderId, String address, String agreementId, String profile) {
        var requestBody = Map.of(
                "@context", "https://w3id.org/dspace/2025/1/context.jsonld",
                "@type", "TransferRequestMessage",
                "consumerPid", UUID.randomUUID().toString(),
                "callbackAddress", address,
                "agreementId", agreementId,
                "format", profile
        );
        var response = send(initialDspUrl + "/transfers/request", requestBody, senderId);
        var providerPid = response.get("providerPid").toString();
        return new TransferRequestResult(providerPid, initialDspUrl);
    }

    private Map<String, Object> send(String url, Map<String, String> requestBody, String senderId) {
        try {
            monitor.debug("Send DSP " + requestBody.get("@type"));

            var request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", createAuthorizationToken(senderId))
                    .post(RequestBody.create(mapper.writeValueAsString(requestBody), JSON))
                    .build();

            return execute(request);
        } catch (IOException e) {
            throw new RuntimeException("Failed to send DSP message: " + e.getMessage(), e);
        }

    }

    private String createAuthorizationToken(String senderId) {
        return mapper.writeValueAsString(Map.of(
                "clientId", senderId
        ));
    }

    private Map<String, Object> execute(Request request) throws IOException {
        try (var response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("Failed to send DSP message to %s. Status: %s".formatted(request.url().toString(), response.code()));
            }

            var body = response.body().string();
            if (body.isBlank()) {
                return Collections.emptyMap();
            }
            return mapper.readValue(body, Map.class);
        }
    }

    private static @NonNull Map<String, String> message(String processId, String type) {
        return Map.of(
                "@context", "https://w3id.org/dspace/2025/1/context.jsonld",
                "@type", type,
                "providerPid", processId,
                "consumerPid", UUID.randomUUID().toString()
        );
    }


}
