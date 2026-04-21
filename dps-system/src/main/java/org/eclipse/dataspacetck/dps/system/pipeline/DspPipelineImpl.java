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
import org.eclipse.dataspacetck.dps.system.api.pipeline.DspPipeline;
import org.eclipse.dataspacetck.dps.system.client.HttpDspClient;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class DspPipelineImpl extends AbstractAsyncPipeline<DspPipeline> implements DspPipeline {

    private static final String REQUESTED_PATH = "/transfers/request";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AtomicReference<Map<String, Object>> receivedRequestedMessage = new AtomicReference<>();
    private final AtomicReference<String> counterPartyProcessId = new AtomicReference<>();
    private final HttpDspClient client;

    public DspPipelineImpl(HttpDspClient client, CallbackEndpoint endpoint, Monitor monitor, long waitTime) {
        super(endpoint, monitor, waitTime);
        this.client = client;
    }

    @Override
    public DspPipeline expectTransferRequestedMessage() {
        var latch = new CountDownLatch(1);
        expectLatches.add(latch);
        endpoint.registerProtocolHandler(REQUESTED_PATH, (headers, body) -> {
            try {
                var message = MAPPER.readValue(body, Map.class);
                receivedRequestedMessage.set(message);
                counterPartyProcessId.set((String) message.get("consumerPid"));
                monitor.debug("Received TransferRequestedMessage from control plane, processId=" + counterPartyProcessId.get());
                endpoint.deregisterHandler(REQUESTED_PATH);
                latch.countDown();
                return new HandlerResponse(200, MAPPER.writeValueAsString(Map.of(
                        "@context", "https://w3id.org/dspace/2025/1/context.jsonld",
                        "@type", "TransferProcess",
                        "providerPid", UUID.randomUUID().toString(),
                        "consumerPid", counterPartyProcessId.get()
                )));
            } catch (IOException e) {
                return new HandlerResponse(400, "Failed to parse TransferRequestedMessage: " + e.getMessage());
            }
        });
        return this;
    }

    @Override
    public DspPipeline thenWaitForTransferRequestedMessage() {
        return thenWait("TransferRequestedMessage to be received", () -> receivedRequestedMessage.get() != null);
    }

    @Override
    public DspPipeline thenWaitForTransferToBeInState(String state) {
        return thenWait("transfer to be in state " + state, () -> {
            var id = counterPartyProcessId.get();
            if (id == null) {
                throw new RuntimeException("Cannot signal completion: no actual process ID received from prepare message");
            }
            monitor.debug("Signaling data flow terminate for actual processId=" + id);
            var actualState = client.dspTransferState(id);
            return Objects.equals(actualState, state);
        });
    }

    @Override
    public DspPipeline sendTransferStartMessage(String processId) {
        stages.add(() -> {
            var id = counterPartyProcessId.get();
            if (id == null) {
                throw new RuntimeException("Cannot signal start: no actual process ID received from prepare message");
            }
            monitor.debug("Signaling data flow completion for actual processId=" + id);
            client.sendTransferStartMessage(id);
        });
        return this;
    }

    @Override
    public DspPipeline sendTransferCompletionMessage(String processId) {
        stages.add(() -> {
            var id = counterPartyProcessId.get();
            if (id == null) {
                throw new RuntimeException("Cannot signal completion: no actual process ID received from prepare message");
            }
            monitor.debug("Signaling data flow completion for actual processId=" + id);
            client.sendTransferCompletionMessage(id);
        });
        return this;
    }

    @Override
    public DspPipeline sendTransferTerminationMessage(String processId) {
        stages.add(() -> {
            var id = counterPartyProcessId.get();
            if (id == null) {
                throw new RuntimeException("Cannot signal termination: no actual process ID received from prepare message");
            }
            monitor.debug("Signaling data flow completion for actual processId=" + id);
            client.sendTransferTerminationMessage(id);
        });
        return this;
    }
}
