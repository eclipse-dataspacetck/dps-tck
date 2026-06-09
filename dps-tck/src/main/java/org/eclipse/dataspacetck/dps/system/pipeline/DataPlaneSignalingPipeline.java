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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import org.eclipse.dataspacetck.core.api.pipeline.AbstractAsyncPipeline;
import org.eclipse.dataspacetck.core.api.system.CallbackEndpoint;
import org.eclipse.dataspacetck.core.api.system.HandlerResponse;
import org.eclipse.dataspacetck.core.spi.boot.Monitor;
import org.eclipse.dataspacetck.dps.system.client.DataPlaneClient;

import java.io.IOException;
import java.io.InputStream;
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

    public DataPlaneSignalingPipeline sendDataFlowPrepareMessage(String agreementId, String datasetId, String transferType) {
        stages.add(() -> sendDataFlowPrepareMessage(false, agreementId, datasetId, transferType));
        return this;
    }

    public DataPlaneSignalingPipeline sendDataFlowPrepareMessageAsync(String agreementId, String datasetId, String transferType) {
        stages.add(() -> sendDataFlowPrepareMessage(true, agreementId, datasetId, transferType));
        return this;
    }

    public DataPlaneSignalingPipeline sendDataFlowStartMessage(String agreementId, String datasetId, String transferType) {
        stages.add(() -> sendDataFlowStartMessage(false, agreementId, datasetId, transferType));
        return this;
    }

    public DataPlaneSignalingPipeline sendDataFlowStartMessageAsync(String agreementId, String datasetId, String transferType) {
        stages.add(() -> sendDataFlowStartMessage(true, agreementId, datasetId, transferType));
        return this;
    }

    public DataPlaneSignalingPipeline sendDataFlowStartMessageWithDataAddress(String agreementId, String datasetId, String transferType) {
        stages.add(() -> sendDataFlowStartMessageWithDataAddress(false, agreementId, datasetId, transferType));
        return this;
    }

    public DataPlaneSignalingPipeline sendDataFlowStartMessageWithDataAddressAsync(String agreementId, String datasetId, String transferType) {
        stages.add(() -> sendDataFlowStartMessageWithDataAddress(true, agreementId, datasetId, transferType));
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
            dataPlaneClient.sendCompletedCallback(endpoint.getAddress(), processId, dataFlowId);
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

    private void sendDataFlowPrepareMessage(boolean async, String agreementId, String datasetId, String transferType) {
        var processId = UUID.randomUUID().toString();
        capturedProcessId.set(processId);
        monitor.debug("TCK CP: sending DataFlowPrepareMessage for processId=" + processId);
        var result = dataPlaneClient.prepare(async, endpoint.getAddress(), processId, agreementId, datasetId, transferType);
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

    private void sendDataFlowStartMessage(boolean async, String agreementId, String datasetId, String transferType) {
        var processId = UUID.randomUUID().toString();
        capturedProcessId.set(processId);
        monitor.debug("TCK CP: sending DataFlowStartMessage for processId=" + processId);
        var result = dataPlaneClient.start(async, endpoint.getAddress(), processId, agreementId, datasetId, transferType);
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

    private void sendDataFlowStartMessageWithDataAddress(boolean async, String agreementId, String datasetId, String transferType) {
        var processId = UUID.randomUUID().toString();
        capturedProcessId.set(processId);
        monitor.debug("TCK CP: sending DataFlowStartMessage (with DataAddress) for processId=" + processId);
        Map<String, Object> dataAddress = Map.of("endpointType", "https://w3id.org/idsa/v4.1/HTTP", "endpoint", endpoint.getAddress());
        var result = dataPlaneClient.startWithDataAddress(async, endpoint.getAddress(), processId, agreementId, datasetId, transferType, dataAddress);
        if (result == null || result.dataFlowId() == null) {
            throw new RuntimeException("DataFlowStartMessage response missing dataFlowId");
        }
        var expectedState = async ? "STARTING" : "STARTED";
        if (!expectedState.equals(result.state())) {
            throw new RuntimeException("Expected state %s but got: %s".formatted(expectedState, result.state()));
        }
        capturedDataFlow.set(result);
        monitor.debug("TCK CP: DataFlowStartMessage (with DataAddress) response: dataFlowId=" + result.dataFlowId() + ", state=" + result.state());
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
        } catch (IOException e) {
            var error = Error.builder().message("Failed to parse %s: %s".formatted(message.name(), e.getMessage())).build();
            return new DpsDeserializationResult(null, List.of(error));
        }
    }

    private HandlerResponse badRequest(List<Error> validationErrors) {
        try {
            return new HandlerResponse(400, "Error evaluating body: " + mapper.writeValueAsString(validationErrors));
        } catch (JsonProcessingException e) {
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
