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

package org.eclipse.dataspacetck.dps.system.api.pipeline;

import org.eclipse.dataspacetck.core.api.pipeline.AsyncPipeline;

public interface DspPipeline extends AsyncPipeline<DspPipeline> {

    DspPipeline expectTransferRequestedMessage();

    DspPipeline thenWaitForTransferRequestedMessage();

    DspPipeline thenWaitForTransferToBeInState(String state);

    DspPipeline sendTransferStartMessage(String processId);

    DspPipeline sendTransferCompletionMessage(String processId);

    DspPipeline sendTransferTerminationMessage(String processId);
}
