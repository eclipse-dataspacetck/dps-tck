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

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;

public enum DpsMessage {
    DataFlowPrepareMessage,
    DataFlowStartMessage,
    DataFlowTerminateMessage,
    DataFlowSuspendMessage,
    DataFlowResumeMessage;

    public static final String DSPACE_SIG_NAMESPACE = "https://w3id.org/dspace-sig/v1.0";

    private final Schema validator;

    DpsMessage() {
        validator = SchemaRegistry.builder()
                .schemaIdResolvers(schemaIdResolvers -> schemaIdResolvers
                        .mapPrefix(DSPACE_SIG_NAMESPACE + "/", "classpath:schema/"))
                .build().getSchema(SchemaLocation.of(DSPACE_SIG_NAMESPACE + "/" + name() + ".schema.json"));
    }

    public Schema getValidator() {
        return validator;
    }
}
