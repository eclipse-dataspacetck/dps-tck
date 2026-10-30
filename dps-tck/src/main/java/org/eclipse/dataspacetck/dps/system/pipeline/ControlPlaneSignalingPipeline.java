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
import org.eclipse.dataspacetck.dps.system.client.ControlPlaneClient;
import org.eclipse.dataspacetck.dps.system.client.DspClient;
import org.eclipse.dataspacetck.dps.system.client.DspClient.ProcessIds;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
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
    private final AtomicReference<String> capturedDataFlowId = new AtomicReference<>();
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
            var dataFlowId = controlPlaneClient.triggerDataFlowPreparation(agreementId, datasetId, endpoint.getAddress());
            capturedDataFlowId.set(dataFlowId);
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
            var dataFlowId = capturedDataFlowId.get();
            if (dataFlowId == null) {
                throw new RuntimeException("Cannot send PREPARED callback: no dataFlowId received from async prepare response");
            }
            monitor.debug("TCK: sending PREPARED callback for dataFlowId=" + dataFlowId + " to control-plane");
            controlPlaneClient.notifyPrepared(dataFlowId);
        });
        return this;
    }

    public ControlPlaneSignalingPipeline thenSendStartedCallback() {
        stages.add(() -> {
            var dataFlowId = capturedDataFlowId.get();
            if (dataFlowId == null) {
                throw new RuntimeException("Cannot send STARTED callback: no dataFlowId received from async start response");
            }
            monitor.debug("TCK: sending STARTED callback for dataFlowId=" + dataFlowId + " to control plane");
            controlPlaneClient.notifyStarted(dataFlowId);
        });
        return this;
    }

    public ControlPlaneSignalingPipeline triggerDataFlowPreparationAsync(String agreementId, String datasetId) {
        stages.add(() -> {
            monitor.debug("Triggering async data flow preparation on control plane under test");
            var dataFlowId = controlPlaneClient.triggerDataFlowPreparationAsync(agreementId, datasetId, endpoint.getAddress());
            capturedDataFlowId.set(dataFlowId);
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

    public ControlPlaneSignalingPipeline thenWaitForTransferToBeInState(String state, String senderId) {
        return thenWait("transfer to be in state " + state, () -> {
            var counterParty = lastCounterParty.get();
            if (counterParty == null) {
                throw new RuntimeException("Cannot wait for transfer state: no counter party data has been set");
            }
            var actualState = dspClient.dspTransferState(senderId, counterParty.address(), counterParty.dataFlowId());
            monitor.debug("TCK. DSP: expecting dataFlowId %s state to be %s. Actual state: %s".formatted(counterParty.dataFlowId(), state, actualState));
            return Objects.equals(actualState, state);
        });
    }

    public ControlPlaneSignalingPipeline sendTransferRequestMessage(String senderId, String agreementId, String profile) {
        stages.add(() -> {
            monitor.debug("Send DSP TransferRequestMessage");
            var requestResult = dspClient.sendTransferRequestMessage(senderId, endpoint.getAddress(), agreementId, profile);
            lastCounterParty.set(new CounterParty(requestResult.processId(), requestResult.address(),
                    new ProcessIds(requestResult.consumerPid(), requestResult.processId())));
        });
        return this;
    }

    public ControlPlaneSignalingPipeline sendTransferStartMessage(String senderId) {
        stages.add(() -> {
            var counterParty = lastCounterParty.get();
            if (counterParty == null) {
                throw new RuntimeException("Cannot signal start: no counter party data has been set");
            }
            monitor.debug("TCK. DSP: send TransferStartMessage for dataFlowId=" + counterParty.dataFlowId());
            dspClient.sendTransferStartMessage(senderId, counterParty.address(), counterParty.dataFlowId(), counterParty.pids());
        });
        return this;
    }

    /**
     * Sends a {@code TransferStartMessage} carrying {@code dataAddress}, as a provider does for a pull transfer.
     */
    public ControlPlaneSignalingPipeline sendTransferStartMessage(String senderId, Map<String, Object> dataAddress) {
        stages.add(() -> {
            var counterParty = lastCounterParty.get();
            if (counterParty == null) {
                throw new RuntimeException("Cannot signal start: no counter party data has been set");
            }
            monitor.debug("TCK. DSP: send TransferStartMessage with a data address for dataFlowId=" + counterParty.dataFlowId());
            dspClient.sendTransferStartMessage(senderId, counterParty.address(), counterParty.dataFlowId(), counterParty.pids(), dataAddress);
        });
        return this;
    }

    /**
     * Verifies that the last {@code DataFlowStartedNotificationMessage} hands the data plane the data address the
     * provider sent: the same endpoint type and endpoint, and every endpoint property by name and value.
     */
    @SuppressWarnings("unchecked")
    public ControlPlaneSignalingPipeline thenVerifyStartedNotificationDataAddress(Map<String, Object> sent) {
        return then(() -> {
            var received = lastDpsReceivedMessage.get();
            assertThat(received).as("DataFlowStartedNotificationMessage").isNotNull();
            var dataAddress = (Map<String, Object>) received.content().get("dataAddress");
            assertThat(dataAddress).as("dataAddress of the DataFlowStartedNotificationMessage").isNotNull();
            assertThat(dataAddress.get("endpointType")).as("endpointType").isEqualTo(sent.get("endpointType"));
            assertThat(dataAddress.get("endpoint")).as("endpoint").isEqualTo(sent.get("endpoint"));
            var receivedProperties = properties(dataAddress);
            properties(sent).forEach((name, value) ->
                    assertThat(receivedProperties).as("endpoint property " + name).containsEntry(name, value));
        });
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> properties(Map<String, Object> dataAddress) {
        var properties = (List<Map<String, Object>>) dataAddress.getOrDefault("endpointProperties", List.of());
        var byName = new HashMap<Object, Object>();
        properties.forEach(p -> byName.put(p.get("name"), p.get("value")));
        return byName;
    }

    public ControlPlaneSignalingPipeline sendTransferCompletionMessage(String senderId) {
        stages.add(() -> {
            var counterParty = lastCounterParty.get();
            if (counterParty == null) {
                throw new RuntimeException("Cannot signal completion: no counter party data has been set");
            }
            monitor.debug("TCK. DSP: send TransferCompletionMessage for dataFlowId=" + counterParty.dataFlowId());
            dspClient.sendTransferCompletionMessage(senderId, counterParty.address(), counterParty.dataFlowId(), counterParty.pids());
        });
        return this;
    }

    public ControlPlaneSignalingPipeline sendTransferTerminationMessage(String senderId) {
        stages.add(() -> {
            var counterParty = lastCounterParty.get();
            if (counterParty == null) {
                throw new RuntimeException("Cannot signal termination: no counter party data has been set");
            }
            monitor.debug("TCK. DSP: send TransferTerminationMessage for dataFlowId=" + counterParty.dataFlowId());
            dspClient.sendTransferTerminationMessage(senderId, counterParty.address(), counterParty.dataFlowId(), counterParty.pids());
        });
        return this;
    }

    public ControlPlaneSignalingPipeline sendTransferSuspensionMessage(String senderId) {
        stages.add(() -> {
            var counterParty = lastCounterParty.get();
            if (counterParty == null) {
                throw new RuntimeException("Cannot signal suspension: no counter party data has been set");
            }
            monitor.debug("TCK. DSP: send TransferSuspensionMessage for dataFlowId=" + counterParty.dataFlowId());
            dspClient.sendTransferSuspensionMessage(senderId, counterParty.address(), counterParty.dataFlowId(), counterParty.pids());
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

                    var dataFlowId = (String) message.get("dataFlowId");
                    capturedDataFlowId.set(dataFlowId);
                    lastDpsReceivedMessage.set(new ReceivedDpsMessage(path, dspMessage, message));
                    monitor.debug("Received async call to %s endpoint. Responding 202/%s, dataFlowId=%s".formatted(path, transitionState, dataFlowId));
                    endpoint.deregisterHandler(path);
                    latch.countDown();

                    try {
                        var responseBody = Map.of("dataFlowId", dataFlowId, "state", transitionState);
                        return new HandlerResponse(202, mapper.writeValueAsString(responseBody),
                                Map.of("Location", "/dataflows/" + dataFlowId + "/status"));
                    } catch (JacksonException e) {
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
                var providerProcessId = UUID.randomUUID().toString();
                lastCounterParty.set(new CounterParty(consumerProcessId, callbackAddress, new ProcessIds(consumerProcessId, providerProcessId)));
                monitor.debug("Received TransferRequestMessage from control plane: %s, dataFlowId=%s".formatted(message, consumerProcessId));
                return new HandlerResponse(200, mapper.writeValueAsString(Map.of(
                        "@context", List.of("https://w3id.org/dspace/2025/1/context.jsonld"),
                        "@type", "TransferProcess",
                        "providerPid", providerProcessId,
                        "consumerPid", consumerProcessId
                )));
            } catch (JacksonException e) {
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
                monitor.debug("Received TransferStartMessage from control plane: %s. dataFlowId=%s".formatted(message, providerProcessId));
                return new HandlerResponse(200, mapper.writeValueAsString(Map.of(
                        "@context", List.of("https://w3id.org/dspace/2025/1/context.jsonld"),
                        "@type", "TransferProcess",
                        "providerPid", providerProcessId,
                        "consumerPid", Optional.ofNullable(lastCounterParty.get()).map(c -> c.pids().consumerPid())
                                .orElseGet(() -> (String) message.get("consumerPid"))
                )));
            } catch (JacksonException e) {
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
        } catch (JacksonException e) {
            var error = Error.builder().message("Failed to parse %s: %s".formatted(message.name(), e.getMessage())).build();
            return new DpsDeserializationResult(null, List.of(error));
        }
    }

    private HandlerResponse success(Map<String, String> responseBody) {
        try {
            return new HandlerResponse(200, mapper.writeValueAsString(responseBody));
        } catch (JacksonException e) {
            return new HandlerResponse(500, "Unexpected exception: " + e.getMessage());
        }
    }

    private HandlerResponse badRequest(List<Error> validationErrors) {
        try {
            return new HandlerResponse(400, "Error evaluating body: " + mapper.writeValueAsString(validationErrors));
        } catch (JacksonException e) {
            return new HandlerResponse(500, "Unexpected exception: " + e.getMessage());
        }
    }

    record ReceivedDpsMessage(String path, DpsMessage type, Map<String, Object> content) {}

    /**
     * The control plane under test as counter-party: its own process id (the {@code dataFlowId} and URL path id),
     * its DSP address, and both process ids of the transfer.
     */
    record CounterParty(String dataFlowId, String address, ProcessIds pids) {}
}
