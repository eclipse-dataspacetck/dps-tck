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

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.eclipse.dataspacetck.core.spi.boot.Monitor;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private final AtomicReference<String> pendingPrepareAgreementId = new AtomicReference<>();
    private final OkHttpClient httpClient = new OkHttpClient();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Monitor monitor;

    public LocalControlPlaneConnector(Monitor monitor) {
        this.monitor = monitor;
    }

    public String triggerDataFlowPreparation(String agreementId, String datasetId, String dataPlaneUrl) {
        dataPlaneBaseUrl.set(dataPlaneUrl);
        var dataFlowId = UUID.randomUUID().toString();
        var consumerPid = UUID.randomUUID().toString();

        sendPrepareMessage(dataPlaneUrl, dataFlowId, agreementId, datasetId);
        sendDspTransferRequestMessage(dataPlaneUrl, consumerPid, agreementId);

        transferStates.put(consumerPid, "REQUESTED");
        return dataFlowId;
    }

    /**
     * Triggers data flow preparation without immediately sending the DSP TransferRequestMessage.
     * The DSP TransferRequestMessage is deferred until {@link #receivePreparedCallback} is called,
     * simulating a CUT that waits for the async /dataflow/prepared callback before proceeding.
     */
    public String triggerDataFlowPreparationAsync(String agreementId, String datasetId, String dataPlaneUrl) {
        dataPlaneBaseUrl.set(dataPlaneUrl);
        pendingPrepareAgreementId.set(agreementId);
        var dataFlowId = UUID.randomUUID().toString();
        sendPrepareMessage(dataPlaneUrl, dataFlowId, agreementId, datasetId);
        return dataFlowId;
    }

    /**
     * Simulates the CUT receiving the async /dataflow/prepared callback.
     * After receiving it, the CUT sends the DSP TransferRequestMessage.
     */
    public void receivePreparedCallback(String dataFlowId) {
        var agreementId = pendingPrepareAgreementId.get();
        var dataPlaneUrl = dataPlaneBaseUrl.get();
        monitor.debug("Local CUT: received PREPARED callback for dataFlowId=" + dataFlowId + ", sending TransferRequestMessage");
        var consumerPid = UUID.randomUUID().toString();
        sendDspTransferRequestMessage(dataPlaneUrl, consumerPid, agreementId);
        transferStates.put(consumerPid, "REQUESTED");
    }

    /**
     * Simulates the CUT receiving the async /dataflow/started callback (provider side).
     * The CUT transitions the transfer to STARTED state.
     */
    public void receiveStartedCallback(String dataFlowId) {
        monitor.debug("Local CUT: received STARTED callback for dataFlowId=" + dataFlowId);
        transferStates.put(dataFlowId, "STARTED");
    }

    public String getTransferState(String dataFlowId) {
        return transferStates.getOrDefault(dataFlowId, "UNKNOWN");
    }

    public void receiveTransferStart(String dataFlowId) {
        var currentState = transferStates.get(dataFlowId);
        if (currentState != null && currentState.equals("SUSPENDED")) {
            monitor.debug("Local CUT: transfer resumed for dataFlowId=" + dataFlowId);
            transferStates.put(dataFlowId, "RESUMED");
            var baseUrl = dataPlaneBaseUrl.get();
            if (baseUrl != null) {
                var message = Map.of("messageId", UUID.randomUUID().toString(), "dataFlowId", dataFlowId);
                sendAsync(baseUrl + "/dataflows/" + dataFlowId + "/resume", serialize(message), "resume notification");
            }
        } else {
            monitor.debug("Local CUT: transfer started for dataFlowId=" + dataFlowId);
            transferStates.put(dataFlowId, "STARTED");
            var baseUrl = dataPlaneBaseUrl.get();
            if (baseUrl != null) {
                var message = Map.of("messageId", UUID.randomUUID().toString());
                sendAsync(baseUrl + "/dataflows/" + dataFlowId + "/started", serialize(message), "DataFlowStartedNotificationMessage");
            }
        }
    }

    public void receiveTransferCompletion(String dataFlowId) {
        monitor.debug("Local CUT: transfer completed for dataFlowId=" + dataFlowId);
        transferStates.put(dataFlowId, "COMPLETED");
        var baseUrl = dataPlaneBaseUrl.get();
        if (baseUrl != null) {
            var message = Map.of("messageId", UUID.randomUUID().toString());
            sendAsync(baseUrl + "/dataflows/" + dataFlowId + "/completed", serialize(message), "completed notification");
        }
    }

    public void receiveTransferTermination(String dataFlowId) {
        monitor.debug("Local CUT: transfer terminated for dataFlowId=" + dataFlowId);
        transferStates.put(dataFlowId, "TERMINATED");
        var baseUrl = dataPlaneBaseUrl.get();
        if (baseUrl != null) {
            var message = Map.of("messageId", UUID.randomUUID().toString());
            sendAsync(baseUrl + "/dataflows/" + dataFlowId + "/terminate", serialize(message), "terminate notification");
        }
    }

    public void receiveTransferSuspension(String dataFlowId) {
        monitor.debug("Local CUT: transfer suspended for dataFlowId=" + dataFlowId);
        transferStates.put(dataFlowId, "SUSPENDED");
        var baseUrl = dataPlaneBaseUrl.get();
        if (baseUrl != null) {
            var message = Map.of("messageId", UUID.randomUUID().toString(), "reason", "suspended by consumer");
            sendAsync(baseUrl + "/dataflows/" + dataFlowId + "/suspend", serialize(message), "suspend notification");
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

        var message = Map.of(
                "messageId", UUID.randomUUID().toString(),
                "participantId", "local-participant",
                "counterPartyId", "local-counterparty",
                "dataspaceContext", "local-dataspace",
                "dataFlowId", providerPid,
                "agreementId", agreementId,
                "datasetId", UUID.randomUUID().toString(),
                "callbackAddress", Optional.ofNullable(dataPlaneBaseUrl.get()).orElse("local://callback"),
                "profile", "HttpData-PULL",
                "claims", Map.of()
        );

        sendAsync(dataPlaneUrl + "/dataflows/start", serialize(message), "DataFlowStartMessage");

        return providerPid;
    }

    private void sendPrepareMessage(String dataPlaneUrl, String dataFlowId, String agreementId, String datasetId) {
        try {
            var message = Map.of(
                    "messageId", UUID.randomUUID().toString(),
                    "participantId", "local-participant",
                    "counterPartyId", "local-counterparty",
                    "dataspaceContext", "local-dataspace",
                    "dataFlowId", dataFlowId,
                    "agreementId", agreementId,
                    "datasetId", datasetId,
                    "profile", "HttpData-PULL",
                    "claims", Map.of()
            );
            post(dataPlaneUrl + "/dataflows/prepare", serialize(message));
            monitor.debug("Local CUT: sent DataFlowPrepareMessage for dataFlowId=" + dataFlowId);
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
            post(dataPlaneUrl + "/transfers/request", serialize(message));
            monitor.debug("Local CUT: sent TransferRequestMessage with consumerPid=" + consumerPid);
        } catch (IOException e) {
            throw new RuntimeException("Failed to send TransferRequestMessage", e);
        }
    }

    private String serialize(Object object) {
        return MAPPER.writeValueAsString(object);
    }

    private void sendAsync(String url, String body, String description) {
        executor.submit(() -> {
            try {
                post(url, body);
                monitor.debug("Local CUT: sent " + description + " to " + url);
            } catch (IOException e) {
                monitor.enableError().message("Local CUT: failed to send " + description + ": " + e.getMessage());
            }
        });
    }

    private void post(String url, String body) throws IOException {
        var request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body, JSON))
                .build();
        try (var response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("HTTP " + response.code() + " from " + url + ". Body: " + response.body().string());
            }
        }
    }
}
