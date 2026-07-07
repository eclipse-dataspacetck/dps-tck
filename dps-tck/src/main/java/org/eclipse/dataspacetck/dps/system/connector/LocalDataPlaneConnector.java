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
import org.eclipse.dataspacetck.dps.system.client.DataPlaneClient.DataFlowResult;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Embeds the behavior of a data plane under test for local (in-process) TCK execution.
 *
 * <p>Simulates a data plane that processes DPS messages from the TCK acting as the control plane,
 * and optionally sends async callbacks back to the TCK callback endpoint.
 */
public class LocalDataPlaneConnector {

    private static final MediaType JSON = MediaType.get("application/json");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, String> dataFlowStates = new ConcurrentHashMap<>();
    private final OkHttpClient httpClient = new OkHttpClient();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Monitor monitor;

    public LocalDataPlaneConnector(Monitor monitor) {
        this.monitor = monitor;
    }

    public DataFlowResult handlePrepare(String callbackAddress, String processId, boolean async, String profile) {
        var dataFlowId = UUID.randomUUID().toString();
        if (async) {
            dataFlowStates.put(dataFlowId, "PREPARING");
            monitor.debug("Local DP: handling async prepare for processId=" + processId + ", dataFlowId=" + dataFlowId);
            var url = callbackAddress + "/transfers/" + processId + "/dataflow/prepared";
            var message = serialize(Map.of("messageId", UUID.randomUUID().toString(), "dataFlowId", dataFlowId, "state", "PREPARED"));
            var description = "DataFlowStatusMessage (PREPARED) callback";
            sendAsync(url, message, description).whenComplete((c, t) -> {
                if (t == null) {
                    dataFlowStates.put(dataFlowId, "PREPARED");
                }
            });
            return new DataFlowResult(dataFlowId, "PREPARING", null);
        } else {
            dataFlowStates.put(dataFlowId, "PREPARED");
            monitor.debug("Local DP: handling sync prepare for processId=" + processId + ", dataFlowId=" + dataFlowId);
            Map<String, Object> dataAddress = profile.endsWith("-push") ? Map.of("key", "value") : null;
            return new DataFlowResult(dataFlowId, "PREPARED", dataAddress);
        }
    }

    public DataFlowResult handleStart(String callbackAddress, String processId, boolean asyncMode, String profile) {
        var dataFlowId = UUID.randomUUID().toString();
        if (asyncMode) {
            dataFlowStates.put(dataFlowId, "STARTING");
            monitor.debug("Local DP: handling async start for processId=" + processId + ", dataFlowId=" + dataFlowId);
            var url = callbackAddress + "/transfers/" + processId + "/dataflow/started";
            var message = serialize(Map.of("messageId", UUID.randomUUID().toString(), "dataFlowId", dataFlowId, "state", "STARTED"));
            var description = "DataFlowStatusMessage (STARTED) callback";
            sendAsync(url, message, description).whenComplete((c, t) -> {
                if (t == null) {
                    dataFlowStates.put(dataFlowId, "STARTED");
                }
            });
            return new DataFlowResult(dataFlowId, "STARTING", null);
        } else {
            dataFlowStates.put(dataFlowId, "STARTED");
            monitor.debug("Local DP: handling sync start for processId=" + processId + ", dataFlowId=" + dataFlowId);
            Map<String, Object> dataAddress = profile.endsWith("-pull") ? Map.of("key", "value") : null;
            return new DataFlowResult(dataFlowId, "STARTED", dataAddress);
        }
    }

    public void handleStarted(String dataFlowId) {
        monitor.debug("Local DP: received started notification for dataFlowId=" + dataFlowId);
        dataFlowStates.put(dataFlowId, "STARTED");
    }

    public void handleSuspend(String dataFlowId) {
        monitor.debug("Local DP: received suspend for dataFlowId=" + dataFlowId);
        dataFlowStates.put(dataFlowId, "SUSPENDED");
    }

    public DataFlowResult handleResume(String dataFlowId) {
        monitor.debug("Local DP: received resume for dataFlowId=" + dataFlowId);
        dataFlowStates.put(dataFlowId, "STARTED");
        return new DataFlowResult(dataFlowId, "STARTED", null);
    }

    public void handleTerminate(String dataFlowId) {
        monitor.debug("Local DP: received terminate for dataFlowId=" + dataFlowId);
        dataFlowStates.put(dataFlowId, "TERMINATED");
    }

    public void handleCompleted(String dataFlowId) {
        monitor.debug("Local DP: received completed notification for dataFlowId=" + dataFlowId);
        dataFlowStates.put(dataFlowId, "COMPLETED");
    }

    public void sendCompletedCallback(String callbackAddress, String processId, String dataFlowId) {
        monitor.debug("Local DP: sending completed callback for dataFlowId=" + dataFlowId);
        sendAsync(callbackAddress + "/transfers/" + processId + "/dataflow/completed",
                serialize(Map.of("messageId", UUID.randomUUID().toString(), "dataFlowId", dataFlowId, "state", "COMPLETED")),
                "DataFlowStatusMessage (COMPLETED) callback");
    }

    public String getState(String dataFlowId) {
        return dataFlowStates.getOrDefault(dataFlowId, "UNKNOWN");
    }

    private String serialize(Object object) {
        return MAPPER.writeValueAsString(object);
    }

    private CompletableFuture<Void> sendAsync(String url, String body, String description) {
        return CompletableFuture.supplyAsync(() -> {
            var request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(body, JSON))
                    .build();

            try (var response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("HTTP " + response.code() + " from " + url + ". Body: " + response.body().string());
                }
                monitor.debug("Local DP: sent " + description + " to " + url);
                return null;
            } catch (IOException e) {
                monitor.enableError().message("Local DP: failed to send " + description + ": " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, executor);
    }

}
