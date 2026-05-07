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

package org.eclipse.dataspacetck.dps.system.client;

import org.eclipse.dataspacetck.dps.system.api.client.DspClient;
import org.eclipse.dataspacetck.dps.system.connector.LocalControlPlaneConnector;

/**
 * In-process implementation of {@link DspClient} backed by a {@link LocalControlPlaneConnector}.
 * Transfer state is kept in-memory; messages trigger state transitions directly.
 */
public class LocalDspClient implements DspClient {

    private final LocalControlPlaneConnector connector;

    public LocalDspClient(LocalControlPlaneConnector connector) {
        this.connector = connector;
    }

    @Override
    public String dspTransferState(String processId) {
        return connector.getTransferState(processId);
    }

    @Override
    public void sendTransferStartMessage(String processId) {
        connector.receiveTransferStart(processId);
    }

    @Override
    public void sendTransferCompletionMessage(String processId) {
        connector.receiveTransferCompletion(processId);
    }

    @Override
    public void sendTransferTerminationMessage(String processId) {
        connector.receiveTransferTermination(processId);
    }

    @Override
    public String sendTransferRequestMessage(String address, String agreementId, String transferType) {
        return connector.receiveTransferRequestMessage(address, agreementId);
    }

    @Override
    public void sendTransferSuspensionMessage(String processId) {
        connector.receiveTransferSuspension(processId);
    }

}
