/*
 *  Copyright (c) 2024 Metaform Systems, Inc.
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Metaform Systems, Inc. - initial API and implementation
 *
 */

package org.eclipse.dataspacetck.dps.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.dataspacetck.core.api.system.CallbackEndpoint;
import org.eclipse.dataspacetck.core.spi.boot.Monitor;
import org.eclipse.dataspacetck.core.spi.system.ServiceConfiguration;
import org.eclipse.dataspacetck.core.spi.system.ServiceResolver;
import org.eclipse.dataspacetck.core.spi.system.SystemConfiguration;
import org.eclipse.dataspacetck.core.spi.system.SystemLauncher;
import org.eclipse.dataspacetck.dps.system.client.http.HttpControlPlaneClient;
import org.eclipse.dataspacetck.dps.system.client.http.HttpDspClient;
import org.eclipse.dataspacetck.dps.system.client.local.LocalControlPlaneClient;
import org.eclipse.dataspacetck.dps.system.client.local.LocalDspClient;
import org.eclipse.dataspacetck.dps.system.connector.LocalControlPlaneConnector;
import org.eclipse.dataspacetck.dps.system.pipeline.ControlPlaneSignalingPipeline;
import org.jspecify.annotations.NonNull;

import static org.eclipse.dataspacetck.core.api.system.SystemsConstants.TCK_PREFIX;

/**
 * Instantiates and bootstraps a DPS test fixture. The test fixture consists of immutable base services and services which
 * are instantiated per test.
 */
public class DpsSystemLauncher implements SystemLauncher {

    private static final String LOCAL_CONNECTOR_CONFIG = TCK_PREFIX + ".dps.local.connector";
    private static final String CONTROL_PLANE_WEBHOOK_URL_CONFIG = TCK_PREFIX + ".dps.controlplane.webhook.url";
    private static final String CONTROL_PLANE_PROTOCOL_URL_CONFIG = TCK_PREFIX + ".dps.controlplane.protocol.url";
    private static final int DEFAULT_WAIT_SECONDS = 15;
    private static final String DEFAULT_WAIT_CONFIG = TCK_PREFIX + ".dps.default.wait";

    private String controlPlaneWebhookUrl;
    private String controlPlaneProtocolUrl;
    private long waitTime = DEFAULT_WAIT_SECONDS;
    private boolean useLocalConnector;
    private Monitor monitor;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void start(SystemConfiguration configuration) {
        monitor = configuration.getMonitor();
        waitTime = configuration.getPropertyAsLong(DEFAULT_WAIT_CONFIG, DEFAULT_WAIT_SECONDS);
        useLocalConnector = configuration.getPropertyAsBoolean(LOCAL_CONNECTOR_CONFIG, false);
        if (!useLocalConnector) {
            controlPlaneWebhookUrl = getRequiredStringSetting(configuration, CONTROL_PLANE_WEBHOOK_URL_CONFIG);
            controlPlaneProtocolUrl = getRequiredStringSetting(configuration, CONTROL_PLANE_PROTOCOL_URL_CONFIG);
        }
    }

    @Override
    public <T> boolean providesService(Class<T> type) {
        return type.equals(ControlPlaneSignalingPipeline.class);
    }

    @Override
    public <T> T getService(Class<T> type, ServiceConfiguration configuration, ServiceResolver resolver) {
        if (ControlPlaneSignalingPipeline.class.equals(type)) {
            var callbackEndpoint = (CallbackEndpoint) resolver.resolve(CallbackEndpoint.class, configuration);
            var pipeline = useLocalConnector ? localPipeline(callbackEndpoint) : httpPipeline(callbackEndpoint);
            return type.cast(pipeline);
        }
        return null;
    }

    private @NonNull ControlPlaneSignalingPipeline httpPipeline(CallbackEndpoint callbackEndpoint) {
        var controlPlaneClient = new HttpControlPlaneClient(controlPlaneWebhookUrl, monitor, mapper);
        var dspClient = new HttpDspClient(controlPlaneProtocolUrl, monitor, mapper);
        return new ControlPlaneSignalingPipeline(controlPlaneClient, dspClient, callbackEndpoint, monitor, waitTime, mapper);
    }

    private @NonNull ControlPlaneSignalingPipeline localPipeline(CallbackEndpoint callbackEndpoint) {
        var connector = new LocalControlPlaneConnector(monitor);
        var controlPlaneClient = new LocalControlPlaneClient(connector);
        var dspClient = new LocalDspClient(connector);
        return new ControlPlaneSignalingPipeline(controlPlaneClient, dspClient, callbackEndpoint, monitor, waitTime, mapper);
    }

    private String getRequiredStringSetting(SystemConfiguration configuration, String key) {
        var setting = configuration.getPropertyAsString(key, null);
        if (setting == null) {
            throw new RuntimeException("Required configuration not set: " + key);
        }
        return setting;
    }

}
