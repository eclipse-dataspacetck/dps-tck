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

import java.util.Map;

public interface DspClient {

    String dspTransferState(String senderId, String callbackAddress, String processId);

    void sendTransferStartMessage(String senderId, String callbackAddress, String processId, ProcessIds pids);

    /**
     * Sends a {@code TransferStartMessage} carrying the provider's {@code dataAddress} (DSP format), as a provider does
     * for pull transfers.
     */
    void sendTransferStartMessage(String senderId, String callbackAddress, String processId, ProcessIds pids, Map<String, Object> dataAddress);

    void sendTransferCompletionMessage(String senderId, String callbackAddress, String processId, ProcessIds pids);

    void sendTransferTerminationMessage(String senderId, String callbackAddress, String processId, ProcessIds pids);

    TransferRequestResult sendTransferRequestMessage(String senderId, String address, String agreementId, String profile);

    void sendTransferSuspensionMessage(String senderId, String callbackAddress, String processId, ProcessIds pids);

    /**
     * The result of a {@code TransferRequestMessage}: the provider's process id ({@code providerPid}), the consumer's
     * ({@code consumerPid}) and the provider's address.
     */
    record TransferRequestResult(String processId, String consumerPid, String address) {}

    /**
     * Both process ids of a transfer process, as every message after the request carries them. {@code processId}
     * in the methods above is the receiver's own id, used in the URL path.
     */
    record ProcessIds(String consumerPid, String providerPid) {}
}
