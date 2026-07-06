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

import com.nimbusds.jwt.SignedJWT;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.eclipse.dataspacetck.core.spi.boot.Monitor;
import org.eclipse.dataspacetck.dps.system.client.DataPlaneClient.DataFlowResult;
import org.eclipse.dataspacetck.dps.system.client.DataPlaneClient.TokenRefreshResult;
import org.eclipse.dataspacetck.dps.system.pipeline.HttpDataAddresses;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final Set<String> issuedRefreshTokens = ConcurrentHashMap.newKeySet();
    private final OkHttpClient httpClient = new OkHttpClient();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Monitor monitor;
    private final String clientDid;
    private final String providerDid;

    public LocalDataPlaneConnector(Monitor monitor, String clientDid, String providerDid) {
        this.monitor = monitor;
        this.clientDid = clientDid;
        this.providerDid = providerDid;
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
            // In push flows the consumer data plane generates the DataAddress the provider will push to.
            Map<String, Object> dataAddress = profile.endsWith("-push")
                    ? HttpDataAddresses.httpPush("https://consumer.example.com/ingest/" + dataFlowId, UUID.randomUUID().toString())
                    : null;
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
            // In pull flows the provider data plane generates the DataAddress the consumer will pull from,
            // including the Token Renewal profile properties.
            Map<String, Object> dataAddress = null;
            if (profile.endsWith("-pull")) {
                var refreshToken = UUID.randomUUID().toString();
                issuedRefreshTokens.add(refreshToken);
                dataAddress = HttpDataAddresses.httpPullWithRenewal(
                        "https://provider.example.com/data/" + dataFlowId,
                        UUID.randomUUID().toString(),
                        refreshToken,
                        "300",
                        "https://provider.example.com/authorization/refresh");
            }
            return new DataFlowResult(dataFlowId, "STARTED", dataAddress);
        }
    }

    public void handleStarted(String dataFlowId) {
        monitor.debug("Local DP: received started notification for dataFlowId=" + dataFlowId);
        dataFlowStates.put(dataFlowId, "STARTED");
    }

    public void handleStarted(String dataFlowId, Map<String, Object> dataAddress) {
        monitor.debug("Local DP: received started notification with DataAddress for dataFlowId=" + dataFlowId);
        dataFlowStates.put(dataFlowId, "STARTED");
    }

    /**
     * Handles a started notification for a consumer that requires the Token Renewal profile: rejects the
     * DataAddress if it does not carry the renewal properties, otherwise transitions to STARTED.
     *
     * @return {@code true} if the notification was rejected.
     */
    public boolean handleStartedRejectingNonRenewable(String dataFlowId, Map<String, Object> dataAddress) {
        if (!hasRenewalProperties(dataAddress)) {
            monitor.debug("Local DP: rejecting started notification - DataAddress lacks Token Renewal properties");
            return true;
        }
        monitor.debug("Local DP: received renewable started notification for dataFlowId=" + dataFlowId);
        dataFlowStates.put(dataFlowId, "STARTED");
        return false;
    }

    private boolean hasRenewalProperties(Map<String, Object> dataAddress) {
        if (dataAddress == null || !(dataAddress.get("endpointProperties") instanceof List<?> properties)) {
            return false;
        }
        var hasRefreshToken = false;
        var hasExpiresIn = false;
        var hasRefreshEndpoint = false;
        for (var element : properties) {
            if (element instanceof Map<?, ?> entry) {
                var name = String.valueOf(entry.get("name"));
                hasRefreshToken |= HttpDataAddresses.REFRESH_TOKEN.equals(name);
                hasExpiresIn |= HttpDataAddresses.EXPIRES_IN.equals(name);
                hasRefreshEndpoint |= HttpDataAddresses.REFRESH_ENDPOINT.equals(name);
            }
        }
        return hasRefreshToken && hasExpiresIn && hasRefreshEndpoint;
    }

    public TokenRefreshResult handleRefresh(String clientAuthJwt, String refreshToken) {
        monitor.debug("Local DP: handling token renewal request");
        if (!issuedRefreshTokens.contains(refreshToken)) {
            monitor.debug("Local DP: rejecting token renewal - unknown refresh token");
            return new TokenRefreshResult(true, null);
        }
        if (!isClientAuthenticationValid(clientAuthJwt)) {
            return new TokenRefreshResult(true, null);
        }
        return new TokenRefreshResult(false, Map.of(
                "access_token", UUID.randomUUID().toString(),
                "token_type", "bearer",
                "expires_in", "300",
                "refresh_token", UUID.randomUUID().toString()
        ));
    }

    /**
     * Lightweight client-authentication check: decodes the JWT and compares {@code iss}/{@code sub}/{@code aud}
     * against the expected DIDs. The signature is not verified in local mode (a real provider verifies it
     * via DID resolution).
     */
    private boolean isClientAuthenticationValid(String clientAuthJwt) {
        try {
            var claims = SignedJWT.parse(clientAuthJwt).getJWTClaimsSet();
            if (!clientDid.equals(claims.getIssuer())) {
                monitor.debug("Local DP: rejecting token renewal - unexpected issuer " + claims.getIssuer());
                return false;
            }
            if (!claims.getIssuer().equals(claims.getSubject())) {
                monitor.debug("Local DP: rejecting token renewal - issuer and subject do not match");
                return false;
            }
            if (claims.getAudience() == null || !claims.getAudience().contains(providerDid)) {
                monitor.debug("Local DP: rejecting token renewal - unexpected audience " + claims.getAudience());
                return false;
            }
            return true;
        } catch (ParseException e) {
            monitor.debug("Local DP: rejecting token renewal - malformed client authentication token");
            return false;
        }
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
