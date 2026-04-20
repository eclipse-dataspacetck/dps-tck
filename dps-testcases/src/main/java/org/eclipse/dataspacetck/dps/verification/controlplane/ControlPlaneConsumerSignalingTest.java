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

package org.eclipse.dataspacetck.dps.verification.controlplane;

import org.eclipse.dataspacetck.api.system.MandatoryTest;
import org.eclipse.dataspacetck.api.system.TestSequenceDiagram;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("base-compliance")
@DisplayName("CN_C_01: Contract request consumer scenarios")
public class ControlPlaneConsumerSignalingTest {

    @MandatoryTest
    @DisplayName("CP_C_S:01-01: Prepare DataFlow")
    @TestSequenceDiagram("""
            data-plane TCK as Technology Compatibility Kit (provider)
            control-plane CUT as Connector Under Test (consumer)
            
            TCK->>CUT: Signal to start preparation
            
            CUT->>TCK: DataFlowPrepareMessage
            TCK-->>CUT: 200 OK
            """)
    public void cn_c_01_01() {
        /**
         * Tell the control-plane to call the prepare (Transfer Process?? or extension that fakes it (see DSP TP))
         * There should be the HTTP server waiting for request, validating json schema, and short-circuiting the call, responding 200
         *
         */
        assertThat(false).isTrue();
    }
}
