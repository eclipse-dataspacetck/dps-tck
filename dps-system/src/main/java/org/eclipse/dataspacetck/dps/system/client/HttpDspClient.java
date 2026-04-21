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
import org.eclipse.dataspacetck.dps.system.api.client.DspClient;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

public class HttpDspClient implements DspClient {

    private static final MediaType JSON = MediaType.get("application/json");
    private final String protocolUrl;
    private final Monitor monitor;
    private final OkHttpClient httpClient;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public HttpDspClient(String protocolUrl, Monitor monitor) {
        this.protocolUrl = protocolUrl;
        this.monitor = monitor;
        this.httpClient = new OkHttpClient();
    }

    @Override
    public String dspTransferState(String processId) {
        try {
            var url = protocolUrl + "/transfers/" + processId;

            var request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", MAPPER.writeValueAsString(Map.of(
                            "clientId","providerId"
                    )))
                    .get()
                    .build();

            monitor.debug("DSP GET TransferProcess " + url);
            try (var response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("Failed to signal data flow completion: HTTP " + response.code());
                }

                return MAPPER.readValue(response.body().byteStream(), Map.class).get("state").toString();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to signal data flow completion", e);
        }
    }

    @Override
    public void sendTransferStartMessage(String processId) {
        send(protocolUrl + "/transfers/" + processId + "/start", processId, "TransferStartMessage");
    }

    @Override
    public void sendTransferCompletionMessage(String processId) {
        send(protocolUrl + "/transfers/" + processId + "/completion", processId, "TransferCompletionMessage");
    }

    @Override
    public void sendTransferTerminationMessage(String processId) {
        send(protocolUrl + "/transfers/" + processId + "/termination", processId, "TransferTerminationMessage");
    }

    private void send(String url, String processId, String type) {
        try {
            monitor.debug("Send DSP " + type);

            var body = message(processId, type);
            var request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", MAPPER.writeValueAsString(Map.of(
                            "clientId","providerId"
                    )))
                    .post(RequestBody.create(MAPPER.writeValueAsString(body), JSON))
                    .build();

            monitor.debug("DSP TransferTerminationMessage " + url);
            try (var response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("Failed to send DSP message to %s. Status: %s".formatted(url, response.code()));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to send DSP message", e);
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
