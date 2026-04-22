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

package org.eclipse.dataspacetck.dps.system.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.eclipse.dataspacetck.core.spi.boot.Monitor;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Embeds the behavior of a control plane under test for local (in-process) TCK execution.
 *
 * <p>Consumer-side flow: simulates a control plane that receives a trigger signal, then
 * posts a {@code DataFlowPrepareMessage} and a {@code TransferRequestMessage} to the TCK
 * acting as the data plane.
 *
 * <p>Provider-side flow: simulates a control plane that receives a DSP
 * {@code TransferRequestMessage} from the TCK and asynchronously posts a
 * {@code DataFlowStartMessage} back to the TCK data plane endpoint.
 */
public class LocalControlPlaneConnector {

    private static final MediaType JSON = MediaType.get("application/json");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, String> transferStates = new ConcurrentHashMap<>();
    private final AtomicReference<String> dataPlaneBaseUrl = new AtomicReference<>();
    private final OkHttpClient httpClient = new OkHttpClient();
    private final Monitor monitor;

    public LocalControlPlaneConnector(Monitor monitor) {
        this.monitor = monitor;
    }

    public void triggerDataFlowPreparation(String processId, String agreementId, String datasetId, String dataPlaneUrl) {
        dataPlaneBaseUrl.set(dataPlaneUrl);
        var consumerPid = UUID.randomUUID().toString();

        sendPrepareMessage(dataPlaneUrl, processId, agreementId, datasetId);
        sendDspTransferRequestMessage(dataPlaneUrl, consumerPid, agreementId);

        transferStates.put(consumerPid, "REQUESTED");
    }

    public String getTransferState(String processId) {
        return transferStates.getOrDefault(processId, "UNKNOWN");
    }

    public void receiveTransferStart(String processId) {
        monitor.debug("Local CUT: transfer started for processId=" + processId);
        transferStates.put(processId, "STARTED");
    }

    public void receiveTransferCompletion(String processId) {
        monitor.debug("Local CUT: transfer completed for processId=" + processId);
        transferStates.put(processId, "COMPLETED");
        var baseUrl = dataPlaneBaseUrl.get();
        if (baseUrl != null) {
            sendAsync(baseUrl + "/dataflows/" + processId + "/completed", "{}", "completed notification");
        }
    }

    public void receiveTransferTermination(String processId) {
        monitor.debug("Local CUT: transfer terminated for processId=" + processId);
        transferStates.put(processId, "TERMINATED");
        var baseUrl = dataPlaneBaseUrl.get();
        if (baseUrl != null) {
            sendAsync(baseUrl + "/dataflows/" + processId + "/terminate", "{}", "terminate notification");
        }
    }

    /**
     * Simulates the CUT receiving a DSP TransferRequestMessage from the TCK.
     * Returns a providerPid and asynchronously sends a DataFlowStartMessage to the TCK.
     */
    public String receiveTransferRequestMessage(String dataPlaneUrl, String agreementId) {
        dataPlaneBaseUrl.set(dataPlaneUrl);
        var providerPid = UUID.randomUUID().toString();
        transferStates.put(providerPid, "REQUESTED");
        monitor.debug("Local CUT: received TransferRequestMessage, providerPid=" + providerPid);

        sendAsync(dataPlaneUrl + "/dataflows/start", buildStartMessage(providerPid), "DataFlowStartMessage");

        return providerPid;
    }

    private void sendPrepareMessage(String dataPlaneUrl, String processId, String agreementId, String datasetId) {
        try {
            var message = Map.of(
                    "messageId", UUID.randomUUID().toString(),
                    "participantId", "local-participant",
                    "counterPartyId", "local-counterparty",
                    "dataspaceContext", "local-dataspace",
                    "processId", processId,
                    "agreementId", agreementId,
                    "datasetId", datasetId,
                    "transferType", "HttpData-PULL",
                    "claims", Map.of()
            );
            post(dataPlaneUrl + "/dataflows/prepare", MAPPER.writeValueAsString(message));
            monitor.debug("Local CUT: sent DataFlowPrepareMessage for processId=" + processId);
        } catch (IOException e) {
            throw new RuntimeException("Failed to send DataFlowPrepareMessage", e);
        }
    }

    private void sendDspTransferRequestMessage(String dataPlaneUrl, String consumerPid, String agreementId) {
        try {
            var message = Map.of(
                    "@context", "https://w3id.org/dspace/2025/1/context.jsonld",
                    "@type", "TransferRequestMessage",
                    "consumerPid", consumerPid,
                    "callbackAddress", dataPlaneUrl,
                    "agreementId", agreementId,
                    "format", "HttpData-PULL"
            );
            post(dataPlaneUrl + "/transfers/request", MAPPER.writeValueAsString(message));
            monitor.debug("Local CUT: sent TransferRequestMessage with consumerPid=" + consumerPid);
        } catch (IOException e) {
            throw new RuntimeException("Failed to send TransferRequestMessage", e);
        }
    }

    private String buildStartMessage(String providerPid) {
        try {
            return MAPPER.writeValueAsString(Map.of(
                    "messageId", UUID.randomUUID().toString(),
                    "processId", providerPid
            ));
        } catch (IOException e) {
            throw new RuntimeException("Failed to build DataFlowStartMessage", e);
        }
    }

    private void sendAsync(String url, String body, String description) {
        var t = new Thread(() -> {
            try {
                post(url, body);
                monitor.debug("Local CUT: sent " + description + " to " + url);
            } catch (IOException e) {
                monitor.enableError().message("Local CUT: failed to send " + description + ": " + e.getMessage());
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void post(String url, String body) throws IOException {
        var request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body, JSON))
                .build();
        try (var response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("HTTP " + response.code() + " from " + url);
            }
        }
    }
}
