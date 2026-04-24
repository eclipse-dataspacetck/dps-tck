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
import org.eclipse.dataspacetck.dps.system.api.client.ControlPlaneClient;
import org.eclipse.dataspacetck.dps.system.api.client.DspClient;
import org.eclipse.dataspacetck.dps.system.api.pipeline.ControlPlaneSignalingPipeline;
import org.eclipse.dataspacetck.dps.system.api.pipeline.DpsMessage;

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
import static org.eclipse.dataspacetck.dps.system.api.pipeline.DpsMessage.DataFlowPrepareMessage;
import static org.eclipse.dataspacetck.dps.system.api.pipeline.DpsMessage.DataFlowResumeMessage;
import static org.eclipse.dataspacetck.dps.system.api.pipeline.DpsMessage.DataFlowStartMessage;
import static org.eclipse.dataspacetck.dps.system.api.pipeline.DpsMessage.DataFlowSuspendMessage;
import static org.eclipse.dataspacetck.dps.system.api.pipeline.DpsMessage.DataFlowTerminateMessage;

/**
 * Pipeline that verifies the control plane under test dispatches a {@code DataFlowPrepareMessage}
 * to the TCK acting as the data plane.
 */
public class ControlPlaneSignalingPipelineImpl extends AbstractAsyncPipeline<ControlPlaneSignalingPipeline> implements ControlPlaneSignalingPipeline {

    private static final String PREPARE_PATH = "/dataflows/prepare";
    private static final String START_PATH = "/dataflows/start";
    private static final String COMPLETED_PATH_PATTERN = "/dataflows/[^/]+/completed";
    private static final String TERMINATE_PATH_PATTERN = "/dataflows/[^/]+/terminate";
    private static final String SUSPEND_PATH_PATTERN = "/dataflows/[^/]+/suspend";
    private static final String RESUME_PATH_PATTERN = "/dataflows/[^/]+/resume";

    private static final String DSP_REQUEST_PATH = "/transfers/request";
    private static final String DSP_START_PATH_PATTERN = "/transfers/[^/]+/start";

    private final ControlPlaneClient controlPlaneClient;
    private final DspClient dspClient;
    private final AtomicReference<ReceivedDpsMessage> lastDpsReceivedMessage = new AtomicReference<>();
    private final AtomicReference<Map<String, Object>> lastDspReceivedMessage = new AtomicReference<>();
    private final AtomicReference<String> counterPartyProcessId = new AtomicReference<>();
    private final ObjectMapper mapper;

    public ControlPlaneSignalingPipelineImpl(ControlPlaneClient controlPlaneClient, DspClient dspClient,
                                             CallbackEndpoint endpoint, Monitor monitor,
                                             long waitTime, ObjectMapper mapper) {
        super(endpoint, monitor, waitTime);
        this.controlPlaneClient = controlPlaneClient;
        this.dspClient = dspClient;
        this.mapper = mapper;


        registerDspTransferRequestHandler();
        registerDspTransferStartHandler();
    }

    @Override
    public ControlPlaneSignalingPipeline triggerDataFlowPreparation(String processId, String agreementId, String datasetId) {
        stages.add(() -> {
            monitor.debug("Triggering data flow preparation on control plane under test");
            controlPlaneClient.triggerDataFlowPreparation(processId, agreementId, datasetId, endpoint.getAddress());
        });
        return this;
    }

    @Override
    public ControlPlaneSignalingPipeline expectDataFlowPrepareMessage() {
        registerMessageHandler(PREPARE_PATH, DataFlowPrepareMessage, Map.of("state", "PREPARED"));
        return this;
    }

    @Override
    public ControlPlaneSignalingPipeline expectDataFlowStartMessage() {
        registerMessageHandler(START_PATH, DataFlowStartMessage, Map.of("state", "STARTED"));
        return this;
    }

    @Override
    public ControlPlaneSignalingPipeline expectDataFlowCompletedMessage(String processId) {
        registerMessageHandler(COMPLETED_PATH_PATTERN, null, emptyMap());
        return this;
    }

    @Override
    public ControlPlaneSignalingPipeline expectDataFlowTerminateMessage(String processId) {
        registerMessageHandler(TERMINATE_PATH_PATTERN, DataFlowTerminateMessage, emptyMap());
        return this;
    }

    @Override
    public ControlPlaneSignalingPipeline expectDataFlowSuspendMessage(String processId) {
        registerMessageHandler(SUSPEND_PATH_PATTERN, DataFlowSuspendMessage, emptyMap());
        return this;
    }

    @Override
    public ControlPlaneSignalingPipeline expectDataFlowResumeMessage(String processId) {
        registerMessageHandler(RESUME_PATH_PATTERN, DataFlowResumeMessage, emptyMap());
        return this;
    }

    @Override
    public ControlPlaneSignalingPipeline thenWaitForPrepareMessage() {
        return thenWait("DataFlowPrepareMessage to be received by TCK data plane", lastDpsCallOn(PREPARE_PATH));
    }

    @Override
    public ControlPlaneSignalingPipeline thenWaitForDataFlowStartMessage() {
        return thenWait("DataFlowStartMessage to be received by TCK data plane", lastDpsCallOn(START_PATH));
    }

    @Override
    public ControlPlaneSignalingPipeline thenWaitForCompletedMessage() {
        return thenWait("completed notification to be received by TCK data plane", lastDpsCallOn(COMPLETED_PATH_PATTERN));
    }

    @Override
    public ControlPlaneSignalingPipeline thenWaitForTerminateMessage() {
        return thenWait("DataFlowTerminateMessage to be received by TCK data plane", lastDpsCallOn(TERMINATE_PATH_PATTERN));
    }

    @Override
    public ControlPlaneSignalingPipeline thenWaitForSuspendMessage() {
        return thenWait("DataFlowSuspendMessage to be received by TCK data plane", lastDpsCallOn(SUSPEND_PATH_PATTERN));
    }

    @Override
    public ControlPlaneSignalingPipeline thenWaitForResumeMessage() {
        return thenWait("DataFlowResumeMessage to be received by TCK data plane", lastDpsCallOn(RESUME_PATH_PATTERN));
    }

    private Callable<Boolean> lastDpsCallOn(String path) {
        return () -> lastDpsReceivedMessage.get() != null && lastDpsReceivedMessage.get().path().equals(path);
    }

    @Override
    public ControlPlaneSignalingPipeline thenWaitForTransferRequestMessage() {
        return thenWait("TransferRequestedMessage to be received", () -> lastDspReceivedMessage.get() != null);
    }

    @Override
    public ControlPlaneSignalingPipeline thenWaitForTransferToBeInState(String state) {
        return thenWait("transfer to be in state " + state, () -> {
            var id = counterPartyProcessId.get();
            if (id == null) {
                throw new RuntimeException("Cannot signal completion: no actual process ID received from prepare message");
            }
            monitor.debug("TCK. DSP: request Transfer state for processId=" + id);
            var actualState = dspClient.dspTransferState(id);
            return Objects.equals(actualState, state);
        });
    }

    @Override
    public ControlPlaneSignalingPipeline sendTransferRequestMessage(String agreementId, String transferType) {
        stages.add(() -> {
            monitor.debug("Send DSP TransferRequestMessage");
            var id = dspClient.sendTransferRequestMessage(endpoint.getAddress(), agreementId, transferType);
            counterPartyProcessId.set(id);
        });
        return this;
    }

    @Override
    public ControlPlaneSignalingPipeline sendTransferStartMessage(String processId) {
        stages.add(() -> {
            var id = counterPartyProcessId.get();
            if (id == null) {
                throw new RuntimeException("Cannot signal start: no actual process ID received from prepare message");
            }
            monitor.debug("TCK. DSP: send TransferStartMessage for processId=" + id);
            dspClient.sendTransferStartMessage(id);
        });
        return this;
    }

    @Override
    public ControlPlaneSignalingPipeline sendTransferCompletionMessage(String processId) {
        stages.add(() -> {
            var id = counterPartyProcessId.get();
            if (id == null) {
                throw new RuntimeException("Cannot signal completion: no actual process ID received from prepare message");
            }
            monitor.debug("TCK. DSP: send TransferCompletionMessage for processId=" + id);
            dspClient.sendTransferCompletionMessage(id);
        });
        return this;
    }

    @Override
    public ControlPlaneSignalingPipeline sendTransferTerminationMessage(String processId) {
        stages.add(() -> {
            var id = counterPartyProcessId.get();
            if (id == null) {
                throw new RuntimeException("Cannot signal termination: no actual process ID received from prepare message");
            }
            monitor.debug("TCK. DSP: send TransferTerminationMessage for processId=" + id);
            dspClient.sendTransferTerminationMessage(id);
        });
        return this;
    }

    @Override
    public ControlPlaneSignalingPipeline sendTransferSuspensionMessage(String processId) {
        stages.add(() -> {
            var id = counterPartyProcessId.get();
            if (id == null) {
                throw new RuntimeException("Cannot signal suspension: no actual process ID received");
            }
            monitor.debug("TCK. DSP: send TransferSuspensionMessage for processId=" + id);
            dspClient.sendTransferSuspensionMessage(id);
        });
        return this;
    }

    @Override
    public ControlPlaneSignalingPipeline sendTransferResumptionMessage(String processId) {
        stages.add(() -> {
            var id = counterPartyProcessId.get();
            if (id == null) {
                throw new RuntimeException("Cannot signal resumption: no actual process ID received");
            }
            monitor.debug("TCK. DSP: send TransferResumptionMessage for processId=" + id);
            dspClient.sendTransferResumptionMessage(id);
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

    private void registerDspTransferRequestHandler() {
        endpoint.registerProtocolHandler(DSP_REQUEST_PATH, (headers, body) -> {
            try {
                var message = mapper.readValue(body, Map.class);
                lastDspReceivedMessage.set(message);
                counterPartyProcessId.set((String) message.get("consumerPid"));
                monitor.debug("Received TransferRequestMessage from control plane, processId=" + counterPartyProcessId.get());
                endpoint.deregisterHandler(DSP_REQUEST_PATH);
                return new HandlerResponse(200, mapper.writeValueAsString(Map.of(
                        "@context", "https://w3id.org/dspace/2025/1/context.jsonld",
                        "@type", "TransferProcess",
                        "providerPid", UUID.randomUUID().toString(),
                        "consumerPid", counterPartyProcessId.get()
                )));
            } catch (IOException e) {
                return new HandlerResponse(400, "Failed to parse TransferRequestMessage: " + e.getMessage());
            }
        });
    }

    private void registerDspTransferStartHandler() {
        endpoint.registerProtocolHandler(DSP_START_PATH_PATTERN, (headers, body) -> {
            try {
                var message = mapper.readValue(body, Map.class);
                lastDspReceivedMessage.set(message);
                counterPartyProcessId.set((String) message.get("providerPid"));
                monitor.debug("Received TransferStartMessage from control plane, processId=" + counterPartyProcessId.get());
                endpoint.deregisterHandler(DSP_START_PATH_PATTERN);
                return new HandlerResponse(200, mapper.writeValueAsString(Map.of(
                        "@context", "https://w3id.org/dspace/2025/1/context.jsonld",
                        "@type", "TransferProcess",
                        "providerPid", counterPartyProcessId.get(),
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

    record DpsDeserializationResult(Map<String, Object> content, List<Error> validationErrors) {}

    record ReceivedDpsMessage(String path, DpsMessage type, Map<String, Object> content) {}
}
