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
import org.eclipse.dataspacetck.dps.system.client.DspClient;
import org.jspecify.annotations.NonNull;

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
    public String dspTransferState(String callbackAddress, String processId) {
        try {
            var address = callbackAddress == null ? this.initialDspUrl : callbackAddress;
            var url = address + "/transfers/" + processId;

            var authentication = Map.of("clientId", "providerId");
            var request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", mapper.writeValueAsString(authentication))
                    .get()
                    .build();

            return execute(request).get("state").toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to signal data flow completion", e);
        }
    }

    @Override
    public void sendTransferStartMessage(String callbackAddress, String processId) {
        var address = callbackAddress == null ? this.initialDspUrl : callbackAddress;
        var requestBody = message(processId, "TransferStartMessage");
        send(address + "/transfers/" + processId + "/start", requestBody, "providerId");
    }

    @Override
    public void sendTransferCompletionMessage(String callbackAddress, String processId) {
        var address = callbackAddress == null ? this.initialDspUrl : callbackAddress;
        var requestBody = message(processId, "TransferCompletionMessage");
        send(address + "/transfers/" + processId + "/completion", requestBody, "providerId");
    }

    @Override
    public void sendTransferTerminationMessage(String callbackAddress, String processId) {
        var address = callbackAddress == null ? this.initialDspUrl : callbackAddress;
        var requestBody = message(processId, "TransferTerminationMessage");
        send(address + "/transfers/" + processId + "/termination", requestBody, "providerId");
    }

    @Override
    public void sendTransferSuspensionMessage(String callbackAddress, String processId) {
        var address = callbackAddress == null ? this.initialDspUrl : callbackAddress;
        var requestBody = message(processId, "TransferSuspensionMessage");
        send(address + "/transfers/" + processId + "/suspension", requestBody, "providerId");
    }

    @Override
    public String sendTransferRequestMessage(String address, String agreementId, String transferType) {
        var requestBody = Map.of(
                "@context", "https://w3id.org/dspace/2025/1/context.jsonld",
                "@type", "TransferRequestMessage",
                "consumerPid", UUID.randomUUID().toString(),
                "callbackAddress", address,
                "agreementId", agreementId,
                "format", transferType
        );
        var response = send(initialDspUrl + "/transfers/request", requestBody, "providerId");
        return response.get("providerPid").toString();
    }

    private Map<String, Object> send(String url, Map<String, String> requestBody, String senderId) {
        try {
            monitor.debug("Send DSP " + requestBody.get("@type"));

            var request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", mapper.writeValueAsString(Map.of(
                            "clientId", senderId
                    )))
                    .post(RequestBody.create(mapper.writeValueAsString(requestBody), JSON))
                    .build();

            return execute(request);
        } catch (IOException e) {
            throw new RuntimeException("Failed to send DSP message: " + e.getMessage(), e);
        }

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
