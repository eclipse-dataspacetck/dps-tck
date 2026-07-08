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

package org.eclipse.dataspacetck.dps.system.pipeline;

import com.networknt.schema.Error;
import org.eclipse.dataspacetck.core.api.pipeline.AbstractAsyncPipeline;
import org.eclipse.dataspacetck.core.api.system.CallbackEndpoint;
import org.eclipse.dataspacetck.core.api.system.HandlerResponse;
import org.eclipse.dataspacetck.core.spi.boot.Monitor;
import org.eclipse.dataspacetck.dps.system.client.DataPlaneClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.dataspacetck.dps.system.pipeline.DpsMessage.DataFlowStatusMessage;

/**
 * Pipeline for verifying the data plane signaling behavior.
 * The TCK acts as the control plane and verifies that the data plane under test
 * correctly handles DPS messages and sends proper callbacks.
 */
public class DataPlaneSignalingPipeline extends AbstractAsyncPipeline<DataPlaneSignalingPipeline> {

    private static final String PREPARED_CALLBACK_PATH_PATTERN = "/transfers/[^/]+/dataflow/prepared";
    private static final String STARTED_CALLBACK_PATH_PATTERN = "/transfers/[^/]+/dataflow/started";
    private static final String COMPLETED_CALLBACK_PATH_PATTERN = "/transfers/[^/]+/dataflow/completed";

    private final DataPlaneClient dataPlaneClient;
    private final AtomicReference<String> capturedProcessId = new AtomicReference<>();
    private final AtomicReference<DataPlaneClient.DataFlowResult> capturedDataFlow = new AtomicReference<>();
    private final AtomicBoolean preparedCallbackReceived = new AtomicBoolean();
    private final AtomicBoolean startedCallbackReceived = new AtomicBoolean();
    private final AtomicBoolean completedCallbackReceived = new AtomicBoolean();
    private final ObjectMapper mapper;

    public DataPlaneSignalingPipeline(DataPlaneClient dataPlaneClient, CallbackEndpoint endpoint,
                                      Monitor monitor, long waitTime, ObjectMapper mapper) {
        super(endpoint, monitor, waitTime);
        this.dataPlaneClient = dataPlaneClient;
        this.mapper = mapper;
    }

    public DataPlaneSignalingPipeline sendDataFlowPrepareMessage(String agreementId, String datasetId, String profile) {
        stages.add(() -> sendDataFlowPrepareMessage(false, agreementId, datasetId, profile));
        return this;
    }

    public DataPlaneSignalingPipeline sendDataFlowPrepareMessageAsync(String agreementId, String datasetId, String profile) {
        stages.add(() -> sendDataFlowPrepareMessage(true, agreementId, datasetId, profile));
        return this;
    }

    public DataPlaneSignalingPipeline sendDataFlowStartMessage(String agreementId, String datasetId, String profile) {
        stages.add(() -> sendDataFlowStartMessage(false, agreementId, datasetId, profile));
        return this;
    }

    public DataPlaneSignalingPipeline sendDataFlowStartMessageAsync(String agreementId, String datasetId, String profile) {
        stages.add(() -> sendDataFlowStartMessage(true, agreementId, datasetId, profile));
        return this;
    }

    public DataPlaneSignalingPipeline sendDataFlowStartMessageWithDataAddress(String agreementId, String datasetId, String profile) {
        stages.add(() -> sendDataFlowStartMessageWithDataAddress(false, agreementId, datasetId, profile));
        return this;
    }

    public DataPlaneSignalingPipeline sendDataFlowStartMessageWithDataAddressAsync(String agreementId, String datasetId, String profile) {
        stages.add(() -> sendDataFlowStartMessageWithDataAddress(true, agreementId, datasetId, profile));
        return this;
    }

    public DataPlaneSignalingPipeline sendDataFlowStartMessageWithHttpPushDataAddress(String agreementId, String datasetId, String profile) {
        stages.add(() -> sendDataFlowStartMessageWithHttpPushDataAddress(false, agreementId, datasetId, profile));
        return this;
    }

    public DataPlaneSignalingPipeline sendDataFlowStartMessageWithHttpPushDataAddressAsync(String agreementId, String datasetId, String profile) {
        stages.add(() -> sendDataFlowStartMessageWithHttpPushDataAddress(true, agreementId, datasetId, profile));
        return this;
    }

    public DataPlaneSignalingPipeline sendDataFlowStartedNotificationWithRenewableHttpPullDataAddress() {
        stages.add(() -> {
            var dataFlowId = capturedDataFlow.get().dataFlowId();
            // Always exercise the Token Renewal profile: deliver a renewable http-pull DataAddress.
            var dataAddress = HttpDataAddresses.httpPullWithRenewal(
                    "https://provider.example.com/data/" + dataFlowId,
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    "300",
                    "https://provider.example.com/authorization/refresh");
            monitor.debug("TCK CP: sending started notification with renewable http-pull DataAddress for dataFlowId=" + dataFlowId);
            dataPlaneClient.sendStarted(dataFlowId, dataAddress);
        });
        return this;
    }

    public DataPlaneSignalingPipeline sendDataFlowStartedNotificationWithNonRenewableHttpPullDataAddressExpectingRejection() {
        then(() -> {
            var dataFlowId = capturedDataFlow.get().dataFlowId();
            var dataAddress = HttpDataAddresses.httpPull("https://provider.example.com/data/" + dataFlowId, UUID.randomUUID().toString());
            monitor.debug("TCK CP: sending started notification with a non-renewable http-pull DataAddress, expecting rejection");
            var rejected = dataPlaneClient.startedNotificationRejected(dataFlowId, dataAddress);
            assertThat(rejected).as("consumer must reject an http-pull DataAddress without Token Renewal properties").isTrue();
        });
        return this;
    }

    public DataPlaneSignalingPipeline sendDataFlowStartedNotification() {
        stages.add(() -> {
            var dataFlowId = capturedDataFlow.get().dataFlowId();
            monitor.debug("TCK CP: sending started notification for dataFlowId=" + dataFlowId);
            dataPlaneClient.sendStarted(dataFlowId);
        });
        return this;
    }

    public DataPlaneSignalingPipeline sendDataFlowSuspendMessage() {
        stages.add(() -> {
            var dataFlowId = capturedDataFlow.get().dataFlowId();
            monitor.debug("TCK CP: sending DataFlowSuspendMessage for dataFlowId=" + dataFlowId);
            dataPlaneClient.sendSuspend(dataFlowId);
        });
        return this;
    }

    public DataPlaneSignalingPipeline sendDataFlowResumeMessage() {
        stages.add(() -> {
            var dataFlowId = capturedDataFlow.get().dataFlowId();
            monitor.debug("TCK CP: sending DataFlowResumeMessage for dataFlowId=" + dataFlowId);
            var result = dataPlaneClient.resume(dataFlowId);
            if (result == null) {
                throw new RuntimeException("DataFlowResumeMessage response was null");
            }
            if (!"STARTED".equals(result.state())) {
                throw new RuntimeException("Expected state STARTED after resume but got: " + result.state());
            }
        });
        return this;
    }

    public DataPlaneSignalingPipeline sendDataFlowTerminateMessage() {
        stages.add(() -> {
            var dataFlowId = capturedDataFlow.get().dataFlowId();
            monitor.debug("TCK CP: sending DataFlowTerminateMessage for dataFlowId=" + dataFlowId);
            dataPlaneClient.sendTerminate(dataFlowId);
        });
        return this;
    }

    public DataPlaneSignalingPipeline sendDataFlowCompletedNotification() {
        stages.add(() -> {
            var dataFlowId = capturedDataFlow.get().dataFlowId();
            monitor.debug("TCK CP: sending completed notification for dataFlowId=" + dataFlowId);
            dataPlaneClient.sendCompleted(dataFlowId);
        });
        return this;
    }

    public DataPlaneSignalingPipeline triggerDataPlaneCompletedCallback() {
        stages.add(() -> {
            var dataFlowId = capturedDataFlow.get().dataFlowId();
            var processId = capturedProcessId.get();
            monitor.debug("TCK CP: triggering data plane completed callback for dataFlowId=" + dataFlowId);
            dataPlaneClient.sendCompletedCallback(processId, dataFlowId);
        });
        return this;
    }

    public DataPlaneSignalingPipeline expectPreparedCallback() {
        registerCallbackHandler(PREPARED_CALLBACK_PATH_PATTERN, preparedCallbackReceived);
        return this;
    }

    public DataPlaneSignalingPipeline expectStartedCallback() {
        registerCallbackHandler(STARTED_CALLBACK_PATH_PATTERN, startedCallbackReceived);
        return this;
    }

    public DataPlaneSignalingPipeline expectCompletedCallback() {
        registerCallbackHandler(COMPLETED_CALLBACK_PATH_PATTERN, completedCallbackReceived);
        return this;
    }

    public DataPlaneSignalingPipeline thenWaitForPreparedCallback() {
        return thenWait("PREPARED callback from data plane", preparedCallbackReceived::get);
    }

    public DataPlaneSignalingPipeline thenWaitForStartedCallback() {
        return thenWait("STARTED callback from data plane", startedCallbackReceived::get);
    }

    public DataPlaneSignalingPipeline thenWaitForCompletedCallback() {
        return thenWait("COMPLETED callback from data plane", completedCallbackReceived::get);
    }

    private void sendDataFlowPrepareMessage(boolean async, String agreementId, String datasetId, String profile) {
        var processId = UUID.randomUUID().toString();
        capturedProcessId.set(processId);
        monitor.debug("TCK CP: sending DataFlowPrepareMessage for processId=" + processId);
        var result = dataPlaneClient.prepare(async, processId, agreementId, datasetId, profile);
        if (result == null || result.dataFlowId() == null) {
            throw new RuntimeException("DataFlowPrepareMessage response missing dataFlowId");
        }
        var expectedState = async ? "PREPARING" : "PREPARED";
        if (!expectedState.equals(result.state())) {
            throw new RuntimeException("Expected state %s but got: %s".formatted(expectedState, result.state()));
        }
        capturedDataFlow.set(result);
        monitor.debug("TCK CP: DataFlowPrepareMessage response: dataFlowId=" + result.dataFlowId() + ", state=" + result.state());
    }

    private void sendDataFlowStartMessage(boolean async, String agreementId, String datasetId, String profile) {
        var processId = UUID.randomUUID().toString();
        capturedProcessId.set(processId);
        monitor.debug("TCK CP: sending DataFlowStartMessage for processId=" + processId);
        var result = dataPlaneClient.start(async, processId, agreementId, datasetId, profile);
        if (result == null || result.dataFlowId() == null) {
            throw new RuntimeException("DataFlowStartMessage response missing dataFlowId");
        }
        var expectedState = async ? "STARTING" : "STARTED";
        if (!expectedState.equals(result.state())) {
            throw new RuntimeException("Expected state %s but got: %s".formatted(expectedState, result.state()));
        }
        capturedDataFlow.set(result);
        monitor.debug("TCK CP: DataFlowStartMessage response: dataFlowId=" + result.dataFlowId() + ", state=" + result.state());
    }

    private void sendDataFlowStartMessageWithDataAddress(boolean async, String agreementId, String datasetId, String profile) {
        Map<String, Object> dataAddress = Map.of("endpointType", "https://w3id.org/idsa/v4.1/HTTP", "endpoint", endpoint.getAddress());
        sendStartWithDataAddress(async, agreementId, datasetId, profile, dataAddress, "DataFlowStartMessage (with DataAddress)");
    }

    private void sendDataFlowStartMessageWithHttpPushDataAddress(boolean async, String agreementId, String datasetId, String profile) {
        var dataAddress = HttpDataAddresses.httpPush("https://consumer.example.com/ingest/" + UUID.randomUUID(), UUID.randomUUID().toString());
        sendStartWithDataAddress(async, agreementId, datasetId, profile, dataAddress, "DataFlowStartMessage (with http-push DataAddress)");
    }

    private void sendStartWithDataAddress(boolean async, String agreementId, String datasetId, String profile, Map<String, Object> dataAddress, String label) {
        var processId = UUID.randomUUID().toString();
        capturedProcessId.set(processId);
        monitor.debug("TCK CP: sending " + label + " for processId=" + processId);
        var result = dataPlaneClient.startWithDataAddress(async, processId, agreementId, datasetId, profile, dataAddress);
        if (result == null || result.dataFlowId() == null) {
            throw new RuntimeException(label + " response missing dataFlowId");
        }
        var expectedState = async ? "STARTING" : "STARTED";
        if (!expectedState.equals(result.state())) {
            throw new RuntimeException("Expected state %s but got: %s".formatted(expectedState, result.state()));
        }
        capturedDataFlow.set(result);
        monitor.debug("TCK CP: " + label + " response: dataFlowId=" + result.dataFlowId() + ", state=" + result.state());
    }

    private void registerCallbackHandler(String pathPattern, AtomicBoolean receivedFlag) {
        var latch = new CountDownLatch(1);
        expectLatches.add(latch);
        stages.add(() ->
                endpoint.registerProtocolHandler(pathPattern, (headers, body) -> {
                    var result = deserialize(body, DataFlowStatusMessage);
                    if (result.content() == null) {
                        return badRequest(result.validationErrors());
                    }
                    monitor.debug("Received callback at path pattern " + pathPattern + ". Content: " + result.content());

                    receivedFlag.set(true);
                    endpoint.deregisterHandler(pathPattern);
                    latch.countDown();
                    return new HandlerResponse(200, "");
                })
        );
    }

    private DpsDeserializationResult deserialize(InputStream body, DpsMessage message) {
        try {
            var node = mapper.readValue(body, JsonNode.class);
            var errors = message.getValidator().validate(node);

            if (!errors.isEmpty()) {
                return new DpsDeserializationResult(null, errors);
            }
            return new DpsDeserializationResult(mapper.convertValue(node, Map.class), null);
        } catch (JacksonException e) {
            var error = Error.builder().message("Failed to parse %s: %s".formatted(message.name(), e.getMessage())).build();
            return new DpsDeserializationResult(null, List.of(error));
        }
    }

    private HandlerResponse badRequest(List<Error> validationErrors) {
        try {
            return new HandlerResponse(400, "Error evaluating body: " + mapper.writeValueAsString(validationErrors));
        } catch (JacksonException e) {
            return new HandlerResponse(500, "Unexpected exception: " + e.getMessage());
        }
    }

    public DataPlaneSignalingPipeline expectReceivedDataAddressToBeNull() {
        then(() -> {
            monitor.debug("TCK CP: expecting received data address to be null");
            assertThat(capturedDataFlow.get().dataAddress()).isNull();
        });
        return this;
    }

    public DataPlaneSignalingPipeline expectReceivedDataAddressToBeNonNull() {
        then(() -> {
            monitor.debug("TCK CP: expecting received data address to be null");
            assertThat(capturedDataFlow.get().dataAddress()).isNotNull();
        });
        return this;
    }

    /**
     * Asserts the received DataAddress conforms to the HTTP pull profile format.
     */
    public DataPlaneSignalingPipeline expectReceivedDataAddressToBeValidHttpPull() {
        then(() -> assertValidHttpDataAddress(HttpDataAddresses.HTTP_PULL));
        return this;
    }

    /**
     * Asserts the received DataAddress conforms to the HTTP push profile format.
     */
    public DataPlaneSignalingPipeline expectReceivedDataAddressToBeValidHttpPush() {
        then(() -> assertValidHttpDataAddress(HttpDataAddresses.HTTP_PUSH));
        return this;
    }

    /**
     * Asserts the received pull DataAddress carries valid Token Renewal profile properties.
     */
    public DataPlaneSignalingPipeline expectReceivedDataAddressToHaveValidRenewalProperties() {
        then(() -> {
            monitor.debug("TCK CP: expecting received data address to carry renewal properties");
            var dataAddress = capturedDataFlow.get().dataAddress();
            assertThat(dataAddress).as("DataAddress").isNotNull();
            var properties = endpointProperties(dataAddress);
            assertThat(properties).as("authType must be bearer").containsEntry(HttpDataAddresses.AUTH_TYPE, HttpDataAddresses.BEARER);
            assertThat(properties.get(HttpDataAddresses.REFRESH_TOKEN)).as("refreshToken").isNotBlank();
            assertThat(properties.get(HttpDataAddresses.EXPIRES_IN)).as("expiresIn must be a numeric string of seconds").matches("\\d+");
            assertThat(properties.get(HttpDataAddresses.REFRESH_ENDPOINT)).as("refreshEndpoint must be an HTTP or HTTPS URL").isNotNull().matches("https?://.*");
        });
        return this;
    }

    /**
     * Acts as the data client and drives the token renewal exchange against the {@code refreshEndpoint}
     * carried in the received pull DataAddress, asserting a valid OAuth 2.0 token response.
     */
    public DataPlaneSignalingPipeline thenDriveTokenRenewal() {
        then(() -> {
            var properties = renewalProperties();
            var refreshToken = properties.get(HttpDataAddresses.REFRESH_TOKEN);
            var accessToken = properties.get(HttpDataAddresses.AUTHORIZATION);
            assertThat(refreshToken).as("refreshToken").isNotBlank();
            monitor.debug("TCK data client: driving token renewal at " + properties.get(HttpDataAddresses.REFRESH_ENDPOINT));
            var result = dataPlaneClient.refreshToken(properties.get(HttpDataAddresses.REFRESH_ENDPOINT), refreshToken, accessToken, true);
            assertThat(result.rejected()).as("valid token renewal should be accepted").isFalse();
            var tokenResponse = result.tokenResponse();
            assertThat(tokenResponse).as("token response").isNotNull();
            assertThat(String.valueOf(tokenResponse.get("access_token"))).as("access_token").isNotBlank().isNotEqualTo(accessToken);
            assertThat(tokenResponse.get("token_type")).as("token_type").isNotNull();
        });
        return this;
    }

    /**
     * Drives a token renewal presenting an invalid (never issued) refresh token and asserts the provider rejects it.
     */
    public DataPlaneSignalingPipeline thenDriveTokenRenewalWithInvalidRefreshTokenExpectingRejection() {
        then(() -> {
            var properties = renewalProperties();
            var invalidRefreshToken = "invalid-refresh-token-" + UUID.randomUUID();
            monitor.debug("TCK data client: driving token renewal with an invalid refresh token");
            var result = dataPlaneClient.refreshToken(properties.get(HttpDataAddresses.REFRESH_ENDPOINT), invalidRefreshToken,
                    properties.get(HttpDataAddresses.AUTHORIZATION), true);
            assertThat(result.rejected()).as("provider must reject an invalid refresh token").isTrue();
        });
        return this;
    }

    /**
     * Drives a token renewal presenting an invalid client-authentication token (wrong iss/sub/aud) and asserts
     * the provider rejects it.
     */
    public DataPlaneSignalingPipeline thenDriveTokenRenewalWithInvalidClientAuthenticationExpectingRejection() {
        then(() -> {
            var properties = renewalProperties();
            monitor.debug("TCK data client: driving token renewal with an invalid client-authentication token");
            var result = dataPlaneClient.refreshToken(properties.get(HttpDataAddresses.REFRESH_ENDPOINT),
                    properties.get(HttpDataAddresses.REFRESH_TOKEN), properties.get(HttpDataAddresses.AUTHORIZATION), false);
            assertThat(result.rejected()).as("provider must reject an invalid client-authentication token").isTrue();
        });
        return this;
    }

    private Map<String, String> renewalProperties() {
        var dataAddress = capturedDataFlow.get().dataAddress();
        assertThat(dataAddress).as("DataAddress").isNotNull();
        var properties = endpointProperties(dataAddress);
        assertThat(properties.get(HttpDataAddresses.REFRESH_ENDPOINT)).as("refreshEndpoint").isNotBlank();
        return properties;
    }

    private void assertValidHttpDataAddress(String expectedEndpointType) {
        monitor.debug("TCK CP: expecting received data address to be a valid " + expectedEndpointType + " DataAddress");
        var dataAddress = capturedDataFlow.get().dataAddress();
        assertThat(dataAddress).as("DataAddress").isNotNull();
        assertThat(dataAddress.get("endpointType")).as("endpointType").isEqualTo(expectedEndpointType);
        assertThat(dataAddress.get("endpoint")).as("endpoint").isInstanceOf(String.class);
        assertThat((String) dataAddress.get("endpoint")).as("endpoint must be an HTTP or HTTPS URL").matches("https?://.*");
        var properties = endpointProperties(dataAddress);
        assertThat(properties).as("authType must be bearer").containsEntry(HttpDataAddresses.AUTH_TYPE, HttpDataAddresses.BEARER);
        assertThat(properties.get(HttpDataAddresses.AUTHORIZATION)).as("authorization token").isNotBlank();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> endpointProperties(Map<String, Object> dataAddress) {
        var raw = dataAddress.get("endpointProperties");
        assertThat(raw).as("endpointProperties").isInstanceOf(List.class);
        var result = new HashMap<String, String>();
        for (var entry : (List<Map<String, Object>>) raw) {
            var value = entry.get("value");
            result.put(String.valueOf(entry.get("name")), value == null ? null : String.valueOf(value));
        }
        return result;
    }

    public DataPlaneSignalingPipeline thenWaitForDataFlowToBeInState(String state) {
        thenWait("data flow to be in state " + state, () -> {
            var id = capturedDataFlow.get().dataFlowId();
            var actualState = dataPlaneClient.getStatus(id).state();
            monitor.debug("TCK. CP: expecting DataFlow %s state to be %s. Actual state: %s".formatted(id, state, actualState));
            return Objects.equals(actualState, state);
        });
        return this;
    }
}
