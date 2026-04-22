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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.dataspacetck.core.api.pipeline.AbstractAsyncPipeline;
import org.eclipse.dataspacetck.core.api.system.CallbackEndpoint;
import org.eclipse.dataspacetck.core.api.system.HandlerResponse;
import org.eclipse.dataspacetck.core.spi.boot.Monitor;
import org.eclipse.dataspacetck.dps.system.api.client.ControlPlaneClient;
import org.eclipse.dataspacetck.dps.system.api.client.DspClient;
import org.eclipse.dataspacetck.dps.system.api.pipeline.ControlPlaneSignalingPipeline;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Pipeline that verifies the control plane under test dispatches a {@code DataFlowPrepareMessage}
 * to the TCK acting as the data plane.
 */
public class ControlPlaneSignalingPipelineImpl extends AbstractAsyncPipeline<ControlPlaneSignalingPipeline> implements ControlPlaneSignalingPipeline {

    private static final String PREPARE_PATH = "/dataflows/prepare";
    private static final String START_PATH = "/dataflows/start";
    private static final String COMPLETED_PATH_PATTERN = "/dataflows/[^/]+/completed";
    private static final String TERMINATED_PATH_PATTERN = "/dataflows/[^/]+/terminate";
    private static final String DSP_REQUEST_PATH = "/transfers/request";
    private static final String DSP_START_PATH_PATTERN = "/transfers/[^/]+/start";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ControlPlaneClient controlPlaneClient;
    private final DspClient dspClient;
    private final AtomicReference<Map<String, Object>> receivedPrepareMessage = new AtomicReference<>();
    private final AtomicReference<Map<String, Object>> receivedStartMessage = new AtomicReference<>();
    private final AtomicReference<String> actualProcessId = new AtomicReference<>();
    private final AtomicReference<Boolean> receivedCompletedNotification = new AtomicReference<>();
    private final AtomicReference<Boolean> receivedTerminateMessage = new AtomicReference<>();
    private final AtomicReference<Map<String, Object>> receivedDspRequestMessage = new AtomicReference<>();
    private final AtomicReference<Map<String, Object>> receivedDspStartMessage = new AtomicReference<>();
    private final AtomicReference<String> counterPartyProcessId = new AtomicReference<>();

    public ControlPlaneSignalingPipelineImpl(ControlPlaneClient controlPlaneClient,
                                             DspClient dspClient,
                                             CallbackEndpoint endpoint,
                                             Monitor monitor,
                                             long waitTime) {
        super(endpoint, monitor, waitTime);
        this.controlPlaneClient = controlPlaneClient;
        this.dspClient = dspClient;

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
        var latch = new CountDownLatch(1);
        expectLatches.add(latch);
        stages.add(() ->
                endpoint.registerProtocolHandler(PREPARE_PATH, (headers, body) -> {
                    try {
                        var message = MAPPER.readValue(body, Map.class);
                        receivedPrepareMessage.set(message);
                        actualProcessId.set((String) message.get("processId"));
                        monitor.debug("Received DataFlowPrepareMessage from control plane, processId=" + actualProcessId.get());
                        endpoint.deregisterHandler(PREPARE_PATH);
                        latch.countDown();
                        var statusMessage = Map.of("state", "PREPARED");
                        return new HandlerResponse(200, MAPPER.writeValueAsString(statusMessage));
                    } catch (IOException e) {
                        return new HandlerResponse(400, "Failed to parse DataFlowPrepareMessage: " + e.getMessage());
                    }
                }));
        return this;
    }

    @Override
    public ControlPlaneSignalingPipeline expectDataFlowStartMessage() {
        var latch = new CountDownLatch(1);
        expectLatches.add(latch);
        stages.add(() ->
                endpoint.registerProtocolHandler(START_PATH, (headers, body) -> {
                    try {
                        var message = MAPPER.readValue(body, Map.class);
                        receivedStartMessage.set(message);
                        actualProcessId.set((String) message.get("processId"));
                        monitor.debug("Received DataFlowStartMessage from control plane, processId=" + actualProcessId.get());
                        endpoint.deregisterHandler(START_PATH);
                        latch.countDown();
                        var statusMessage = Map.of("state", "STARTED");
                        return new HandlerResponse(200, MAPPER.writeValueAsString(statusMessage));
                    } catch (IOException e) {
                        return new HandlerResponse(400, "Failed to parse DataFlowStartMessage: " + e.getMessage());
                    }
                }));
        return this;
    }

    @Override
    public ControlPlaneSignalingPipeline thenWaitForPrepareMessage() {
        return thenWait("DataFlowPrepareMessage to be received", () -> receivedPrepareMessage.get() != null);
    }

    @Override
    public ControlPlaneSignalingPipeline thenWaitForDataFlowStartMessage() {
        return thenWait("DataFlowStartMessage to be received", () -> receivedStartMessage.get() != null);
    }

    @Override
    public ControlPlaneSignalingPipeline expectDataFlowCompletedMessage(String processId) {
        var latch = new CountDownLatch(1);
        expectLatches.add(latch);
        stages.add(() ->
                endpoint.registerProtocolHandler(COMPLETED_PATH_PATTERN, (headers, body) -> {
                    receivedCompletedNotification.set(true);
                    monitor.debug("Received completed notification from control plane");
                    endpoint.deregisterHandler(COMPLETED_PATH_PATTERN);
                    latch.countDown();
                    return new HandlerResponse(200, "{}");
                }));
        return this;
    }

    @Override
    public ControlPlaneSignalingPipeline expectDataFlowTerminateMessage(String processId) {
        var latch = new CountDownLatch(1);
        expectLatches.add(latch);
        stages.add(() ->
                endpoint.registerProtocolHandler(TERMINATED_PATH_PATTERN, (headers, body) -> {
                    receivedTerminateMessage.set(true);
                    monitor.debug("Received terminated message from control plane");
                    endpoint.deregisterHandler(TERMINATED_PATH_PATTERN);
                    latch.countDown();
                    return new HandlerResponse(200, "{}");
                }));
        return this;
    }

    @Override
    public ControlPlaneSignalingPipeline thenWaitForCompletedMessage() {
        return thenWait("completed notification to be received by TCK data plane", () -> receivedCompletedNotification.get() != null);
    }

    @Override
    public ControlPlaneSignalingPipeline thenWaitForTerminateMessage() {
        return thenWait("terminate message to be received by TCK data plane", () -> receivedTerminateMessage.get() != null);
    }

    @Override
    public ControlPlaneSignalingPipeline thenWaitForTransferRequestMessage() {
        return thenWait("TransferRequestedMessage to be received", () -> receivedDspRequestMessage.get() != null);
    }

    @Override
    public ControlPlaneSignalingPipeline thenWaitForTransferToBeInState(String state) {
        return thenWait("transfer to be in state " + state, () -> {
            var id = counterPartyProcessId.get();
            if (id == null) {
                throw new RuntimeException("Cannot signal completion: no actual process ID received from prepare message");
            }
            monitor.debug("Send DSP message for actual processId=" + id);
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
            monitor.debug("Send DSP message for actual processId=" + id);
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
            monitor.debug("Send DSP message for actual processId=" + id);
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
            monitor.debug("Send DSP message for actual processId=" + id);
            dspClient.sendTransferTerminationMessage(id);
        });
        return this;
    }

    public Map<String, Object> getReceivedPrepareMessage() {
        return receivedPrepareMessage.get();
    }

    public Map<String, Object> getReceivedStartMessage() {
        return receivedStartMessage.get();
    }

    private void registerDspTransferRequestHandler() {
        endpoint.registerProtocolHandler(DSP_REQUEST_PATH, (headers, body) -> {
            try {
                var message = MAPPER.readValue(body, Map.class);
                receivedDspRequestMessage.set(message);
                counterPartyProcessId.set((String) message.get("consumerPid"));
                monitor.debug("Received TransferRequestMessage from control plane, processId=" + counterPartyProcessId.get());
                endpoint.deregisterHandler(DSP_REQUEST_PATH);
                return new HandlerResponse(200, MAPPER.writeValueAsString(Map.of(
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
                var message = MAPPER.readValue(body, Map.class);
                receivedDspStartMessage.set(message);
                counterPartyProcessId.set((String) message.get("providerPid"));
                monitor.debug("Received TransferStartMessage from control plane, processId=" + counterPartyProcessId.get());
                endpoint.deregisterHandler(DSP_START_PATH_PATTERN);
                return new HandlerResponse(200, MAPPER.writeValueAsString(Map.of(
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
}
