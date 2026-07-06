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

public interface DspClient {

    String dspTransferState(String senderId, String callbackAddress, String processId);

    void sendTransferStartMessage(String senderId, String callbackAddress, String processId);

    void sendTransferCompletionMessage(String senderId, String callbackAddress, String processId);

    void sendTransferTerminationMessage(String senderId, String callbackAddress, String processId);

    TransferRequestResult sendTransferRequestMessage(String senderId, String address, String agreementId, String profile);

    void sendTransferSuspensionMessage(String senderId, String callbackAddress, String processId);

    record TransferRequestResult(String processId, String address) {}
}
