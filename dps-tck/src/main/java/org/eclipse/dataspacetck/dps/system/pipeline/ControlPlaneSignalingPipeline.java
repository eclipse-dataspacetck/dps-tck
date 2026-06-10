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
import org.eclipse.dataspacetck.dps.system.client.ControlPlaneClient;
import org.eclipse.dataspacetck.dps.system.client.DspClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.emptyMap;
import static org.eclipse.dataspacetck.dps.system.pipeline.DpsMessage.DataFlowPrepareMessage;
import static org.eclipse.dataspacetck.dps.system.pipeline.DpsMessage.DataFlowResumeMessage;
import static org.eclipse.dataspacetck.dps.system.pipeline.DpsMessage.DataFlowStartMessage;
import static org.eclipse.dataspacetck.dps.system.pipeline.DpsMessage.DataFlowStartedNotificationMessage;
import static org.eclipse.dataspacetck.dps.system.pipeline.DpsMessage.DataFlowSuspendMessage;
import static org.eclipse.dataspacetck.dps.system.pipeline.DpsMessage.DataFlowTerminateMessage;

/**
 * Pipeline for verifying the control plane consumer signaling behavior.
 * The TCK acts as the data plane and verifies that the control plane under test
 * correctly dispatches a {@code DataFlowPrepareMessage} when triggered.
 */
public class ControlPlaneSignalingPipeline extends AbstractAsyncPipeline<ControlPlaneSignalingPipeline> {

    private static final String PREPARE_PATH = "/dataflows/prepare";
    private static final String START_PATH = "/dataflows/start";
    private static final String STARTED_PATH_PATTERN = "/dataflows/[^/]+/started";
    private static final String COMPLETED_PATH_PATTERN = "/dataflows/[^/]+/completed";
    private static final String TERMINATE_PATH_PATTERN = "/dataflows/[^/]+/terminate";
    private static final String SUSPEND_PATH_PATTERN = "/dataflows/[^/]+/suspend";
    private static final String RESUME_PATH_PATTERN = "/dataflows/[^/]+/resume";

    private static final String PREPARING_STATE = "PREPARING";
    private static final String STARTING_STATE = "STARTING";

    private static final String DSP_REQUEST_PATH = "/transfers/request";
    private static final String DSP_START_PATH_PATTERN = "/transfers/[^/]+/start";

    private final ControlPlaneClient controlPlaneClient;
    private final DspClient dspClient;
    private final AtomicReference<ReceivedDpsMessage> lastDpsReceivedMessage = new AtomicReference<>();
    private final AtomicReference<Map<String, Object>> lastDspReceivedMessage = new AtomicReference<>();
    private final AtomicReference<CounterParty> lastCounterParty = new AtomicReference<>();
    private final AtomicReference<String> capturedProcessId = new AtomicReference<>();
    private final AtomicReference<String> lastReceivedDataFlowId = new AtomicReference<>();
    private final AtomicReference<String> lastReceivedCallbackAddress = new AtomicReference<>();
    private final ObjectMapper mapper;

    public ControlPlaneSignalingPipeline(ControlPlaneClient controlPlaneClient, DspClient dspClient,
                                         CallbackEndpoint endpoint, Monitor monitor,
                                         long waitTime, ObjectMapper mapper) {
        super(endpoint, monitor, waitTime);
        this.controlPlaneClient = controlPlaneClient;
        this.dspClient = dspClient;
        this.mapper = mapper;

        registerDspTransferRequestHandler();
        registerDspTransferStartHandler();
    }

    public ControlPlaneSignalingPipeline triggerDataFlowPreparation(String agreementId, String datasetId) {
        stages.add(() -> {
            monitor.debug("Triggering data flow preparation on control plane under test");
            var processId = controlPlaneClient.triggerDataFlowPreparation(agreementId, datasetId, endpoint.getAddress());
            capturedProcessId.set(processId);
        });
        return this;
    }

    public ControlPlaneSignalingPipeline expectDataFlowPrepareMessage() {
        registerMessageHandler(PREPARE_PATH, DataFlowPrepareMessage, Map.of("state", "PREPARED"));
        return this;
    }

    public ControlPlaneSignalingPipeline expectDataFlowStartMessage() {
        registerMessageHandler(START_PATH, DataFlowStartMessage, Map.of("state", "STARTED"));
        return this;
    }

    public ControlPlaneSignalingPipeline expectDataFlowStartedNotificationMessage() {
        registerMessageHandler(STARTED_PATH_PATTERN, DataFlowStartedNotificationMessage, emptyMap());
        return this;
    }

    public ControlPlaneSignalingPipeline expectDataFlowCompletedMessage() {
        registerMessageHandler(COMPLETED_PATH_PATTERN, null, emptyMap());
        return this;
    }

    public ControlPlaneSignalingPipeline expectDataFlowTerminateMessage() {
        registerMessageHandler(TERMINATE_PATH_PATTERN, DataFlowTerminateMessage, emptyMap());
        return this;
    }

    public ControlPlaneSignalingPipeline expectDataFlowSuspendMessage() {
        registerMessageHandler(SUSPEND_PATH_PATTERN, DataFlowSuspendMessage, emptyMap());
        return this;
    }

    public ControlPlaneSignalingPipeline expectDataFlowResumeMessage() {
        registerMessageHandler(RESUME_PATH_PATTERN, DataFlowResumeMessage, Map.of("state", "STARTED"));
        return this;
    }

    public ControlPlaneSignalingPipeline expectDataFlowPrepareMessageAsync() {
        registerAsyncMessageHandler(PREPARE_PATH, DataFlowPrepareMessage, PREPARING_STATE);
        return this;
    }

    public ControlPlaneSignalingPipeline expectDataFlowStartMessageAsync() {
        registerAsyncMessageHandler(START_PATH, DataFlowStartMessage, STARTING_STATE);
        return this;
    }

    public ControlPlaneSignalingPipeline thenSendPreparedCallback() {
        stages.add(() -> {
            var dataFlowId = lastReceivedDataFlowId.get();
            var callbackAddress = lastReceivedCallbackAddress.get();
            if (dataFlowId == null) {
                throw new RuntimeException("Cannot send PREPARED callback: no dataFlowId received from async prepare response");
            }
            var processId = capturedProcessId.get();
            monitor.debug("TCK: sending PREPARED callback for dataFlowId=" + dataFlowId + " to " + callbackAddress);
            controlPlaneClient.notifyPrepared(callbackAddress, processId, dataFlowId);
        });
        return this;
    }

    public ControlPlaneSignalingPipeline thenSendStartedCallback() {
        stages.add(() -> {
            var dataFlowId = lastReceivedDataFlowId.get();
            var callbackAddress = lastReceivedCallbackAddress.get();
            if (dataFlowId == null) {
                throw new RuntimeException("Cannot send STARTED callback: no dataFlowId received from async start response");
            }
            var processId = capturedProcessId.get();
            monitor.debug("TCK: sending STARTED callback for dataFlowId=" + dataFlowId + " to " + callbackAddress);
            controlPlaneClient.notifyStarted(callbackAddress, processId, dataFlowId);
        });
        return this;
    }

    public ControlPlaneSignalingPipeline triggerDataFlowPreparationAsync(String agreementId, String datasetId) {
        stages.add(() -> {
            monitor.debug("Triggering async data flow preparation on control plane under test");
            var processId = controlPlaneClient.triggerDataFlowPreparationAsync(agreementId, datasetId, endpoint.getAddress());
            capturedProcessId.set(processId);
        });
        return this;
    }

    public ControlPlaneSignalingPipeline thenWaitForPrepareMessage() {
        return thenWait("DataFlowPrepareMessage to be received by TCK data plane", lastDpsCallOn(PREPARE_PATH));
    }

    public ControlPlaneSignalingPipeline thenWaitForDataFlowStartMessage() {
        return thenWait("DataFlowStartMessage to be received by TCK data plane", lastDpsCallOn(START_PATH));
    }

    public ControlPlaneSignalingPipeline thenWaitForCompletedMessage() {
        return thenWait("completed notification to be received by TCK data plane", lastDpsCallOn(COMPLETED_PATH_PATTERN));
    }

    public ControlPlaneSignalingPipeline thenWaitForTerminateMessage() {
        return thenWait("DataFlowTerminateMessage to be received by TCK data plane", lastDpsCallOn(TERMINATE_PATH_PATTERN));
    }

    public ControlPlaneSignalingPipeline thenWaitForSuspendMessage() {
        return thenWait("DataFlowSuspendMessage to be received by TCK data plane", lastDpsCallOn(SUSPEND_PATH_PATTERN));
    }

    public ControlPlaneSignalingPipeline thenWaitForResumeMessage() {
        return thenWait("DataFlowResumeMessage to be received by TCK data plane", lastDpsCallOn(RESUME_PATH_PATTERN));
    }

    public ControlPlaneSignalingPipeline thenWaitForStartedNotificationMessage() {
        return thenWait("DataFlowStartedNotificationMessage to be received by TCK data plane", lastDpsCallOn(STARTED_PATH_PATTERN));
    }

    private Callable<Boolean> lastDpsCallOn(String path) {
        return () -> lastDpsReceivedMessage.get() != null && lastDpsReceivedMessage.get().path().equals(path);
    }

    public ControlPlaneSignalingPipeline thenWaitForTransferRequestMessage() {
        return thenWait("TransferRequestedMessage to be received", () -> lastDspReceivedMessage.get() != null);
    }

    public ControlPlaneSignalingPipeline thenWaitForTransferToBeInState(String state) {
        return thenWait("transfer to be in state " + state, () -> {
            var counterParty = lastCounterParty.get();
            if (counterParty == null) {
                throw new RuntimeException("Cannot signal completion: no actual process ID received from prepare message");
            }
            var actualState = dspClient.dspTransferState(counterParty.address(), counterParty.processId());
            monitor.debug("TCK. DSP: expecting processId %s state to be %s. Actual state: %s".formatted(counterParty.processId(), state, actualState));
            return Objects.equals(actualState, state);
        });
    }

    public ControlPlaneSignalingPipeline sendTransferRequestMessage(String agreementId, String transferType) {
        stages.add(() -> {
            monitor.debug("Send DSP TransferRequestMessage");
            var requestResult = dspClient.sendTransferRequestMessage(endpoint.getAddress(), agreementId, transferType);
            lastCounterParty.set(new CounterParty(requestResult.processId(), requestResult.address()));
        });
        return this;
    }

    public ControlPlaneSignalingPipeline sendTransferStartMessage() {
        stages.add(() -> {
            var counterParty = lastCounterParty.get();
            if (counterParty == null) {
                throw new RuntimeException("Cannot signal start: no actual process ID received from prepare message");
            }
            monitor.debug("TCK. DSP: send TransferStartMessage for processId=" + counterParty.processId());
            dspClient.sendTransferStartMessage(counterParty.address(), counterParty.processId());
        });
        return this;
    }

    public ControlPlaneSignalingPipeline sendTransferCompletionMessage() {
        stages.add(() -> {
            var counterParty = lastCounterParty.get();
            if (counterParty == null) {
                throw new RuntimeException("Cannot signal completion: no actual process ID received from prepare message");
            }
            monitor.debug("TCK. DSP: send TransferCompletionMessage for processId=" + counterParty.processId());
            dspClient.sendTransferCompletionMessage(counterParty.address(), counterParty.processId());
        });
        return this;
    }

    public ControlPlaneSignalingPipeline sendTransferTerminationMessage() {
        stages.add(() -> {
            var counterParty = lastCounterParty.get();
            if (counterParty == null) {
                throw new RuntimeException("Cannot signal termination: no actual process ID received from prepare message");
            }
            monitor.debug("TCK. DSP: send TransferTerminationMessage for processId=" + counterParty.processId());
            dspClient.sendTransferTerminationMessage(counterParty.address(), counterParty.processId());
        });
        return this;
    }

    public ControlPlaneSignalingPipeline sendTransferSuspensionMessage() {
        stages.add(() -> {
            var counterParty = lastCounterParty.get();
            if (counterParty == null) {
                throw new RuntimeException("Cannot signal suspension: no actual process ID received");
            }
            monitor.debug("TCK. DSP: send TransferSuspensionMessage for processId=" + counterParty.processId());
            dspClient.sendTransferSuspensionMessage(counterParty.address(), counterParty.processId());
        });
        return this;
    }

    private void registerMessageHandler(String path, DpsMessage dspMessage, Map<String, String> responseBody) {
        var latch = new CountDownLatch(1);
        expectLatches.add(latch);
        stages.add(() ->
                endpoint.registerProtocolHandler(path, (headers, body) -> {
                    Map<String, Object> message = null;
                    if (dspMessage != null) {
                        var result = deserializeDps(body, dspMessage);
                        message = result.content();
                        if (message == null) {
                            return badRequest(result.validationErrors());
                        }
                    }

                    lastDpsReceivedMessage.set(new ReceivedDpsMessage(path, dspMessage, message));
                    monitor.debug("Received call to %s endpoint from control plane. Content: %s".formatted(path, message));
                    endpoint.deregisterHandler(path);
                    latch.countDown();

                    return success(responseBody);
                }));
    }

    private void registerAsyncMessageHandler(String path, DpsMessage dspMessage, String transitionState) {
        var latch = new CountDownLatch(1);
        expectLatches.add(latch);
        stages.add(() ->
                endpoint.registerProtocolHandler(path, (headers, body) -> {
                    var result = deserializeDps(body, dspMessage);
                    var message = result.content();
                    if (message == null) {
                        return badRequest(result.validationErrors());
                    }

                    var dataFlowId = UUID.randomUUID().toString();
                    var callbackAddress = (String) message.get("callbackAddress");
                    var processId = (String) message.get("processId");
                    lastReceivedDataFlowId.set(dataFlowId);
                    lastReceivedCallbackAddress.set(callbackAddress);
                    capturedProcessId.set(processId);
                    lastDpsReceivedMessage.set(new ReceivedDpsMessage(path, dspMessage, message));
                    monitor.debug("Received async call to %s endpoint. Responding 202/%s, dataFlowId=%s".formatted(path, transitionState, dataFlowId));
                    endpoint.deregisterHandler(path);
                    latch.countDown();

                    try {
                        var responseBody = Map.of("dataFlowId", dataFlowId, "state", transitionState);
                        return new HandlerResponse(202, mapper.writeValueAsString(responseBody),
                                Map.of("Location", "/dataflows/" + dataFlowId + "/status"));
                    } catch (JsonProcessingException e) {
                        return new HandlerResponse(500, "Unexpected exception: " + e.getMessage());
                    }
                }));
    }

    private void registerDspTransferRequestHandler() {
        monitor.message("TCK. DSP: registerDspTransferRequestHandler");
        endpoint.registerProtocolHandler(DSP_REQUEST_PATH, (headers, body) -> {
            try {
                var message = mapper.readValue(body, Map.class);
                lastDspReceivedMessage.set(message);
                var consumerProcessId = (String) message.get("consumerPid");
                var callbackAddress = (String) message.get("callbackAddress");
                lastCounterParty.set(new CounterParty(consumerProcessId, callbackAddress));
                monitor.debug("Received TransferRequestMessage from control plane: %s, processId=%s".formatted(message, consumerProcessId));
                return new HandlerResponse(200, mapper.writeValueAsString(Map.of(
                        "@context", "https://w3id.org/dspace/2025/1/context.jsonld",
                        "@type", "TransferProcess",
                        "providerPid", UUID.randomUUID().toString(),
                        "consumerPid", consumerProcessId
                )));
            } catch (IOException e) {
                return new HandlerResponse(400, "Failed to parse TransferRequestMessage: " + e.getMessage());
            }
        });
    }

    private void registerDspTransferStartHandler() {
        monitor.message("TCK. DSP: registerDspTransferStartHandler");
        endpoint.registerProtocolHandler(DSP_START_PATH_PATTERN, (headers, body) -> {
            try {
                var message = mapper.readValue(body, Map.class);
                lastDspReceivedMessage.set(message);
                var providerProcessId = (String) message.get("providerPid");
                monitor.debug("Received TransferStartMessage from control plane: %s. processId=%s".formatted(message, providerProcessId));
                return new HandlerResponse(200, mapper.writeValueAsString(Map.of(
                        "@context", "https://w3id.org/dspace/2025/1/context.jsonld",
                        "@type", "TransferProcess",
                        "providerPid", providerProcessId,
                        "consumerPid", UUID.randomUUID()
                )));
            } catch (IOException e) {
                return new HandlerResponse(400, "Failed to parse TransferStartMessage: " + e.getMessage());
            }
        });
    }

    private DpsDeserializationResult deserializeDps(InputStream body, DpsMessage message) {
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

    private HandlerResponse success(Map<String, String> responseBody) {
        try {
            return new HandlerResponse(200, mapper.writeValueAsString(responseBody));
        } catch (JsonProcessingException e) {
            return new HandlerResponse(500, "Unexpected exception: " + e.getMessage());
        }
    }

    private HandlerResponse badRequest(List<Error> validationErrors) {
        try {
            return new HandlerResponse(400, "Error evaluating body: " + mapper.writeValueAsString(validationErrors));
        } catch (JsonProcessingException e) {
            return new HandlerResponse(500, "Unexpected exception: " + e.getMessage());
        }
    }

    record ReceivedDpsMessage(String path, DpsMessage type, Map<String, Object> content) {}

    record CounterParty(String processId, String address) {}
}
