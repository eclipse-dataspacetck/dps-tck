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
import org.eclipse.dataspacetck.dps.system.client.http.HttpDataPlaneClient;
import org.eclipse.dataspacetck.dps.system.client.http.HttpDspClient;
import org.eclipse.dataspacetck.dps.system.client.local.LocalControlPlaneClient;
import org.eclipse.dataspacetck.dps.system.client.local.LocalDataPlaneClient;
import org.eclipse.dataspacetck.dps.system.client.local.LocalDspClient;
import org.eclipse.dataspacetck.dps.system.connector.LocalControlPlaneConnector;
import org.eclipse.dataspacetck.dps.system.connector.LocalDataPlaneConnector;
import org.eclipse.dataspacetck.dps.system.pipeline.ControlPlaneSignalingPipeline;
import org.eclipse.dataspacetck.dps.system.pipeline.DataPlaneSignalingPipeline;
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
    private static final String DATA_PLANE_URL_CONFIG = TCK_PREFIX + ".dps.dataplane.url";
    private static final String DATA_PLANE_AUTHORIZATION_CONFIG = TCK_PREFIX + ".dps.dataplane.authorization";
    private static final int DEFAULT_WAIT_SECONDS = 15;
    private static final String DEFAULT_WAIT_CONFIG = TCK_PREFIX + ".dps.default.wait";

    private String controlPlaneWebhookUrl;
    private String controlPlaneProtocolUrl;
    private String dataPlaneUrl;
    private String dataPlaneAuthorization;
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
            controlPlaneWebhookUrl = configuration.getPropertyAsString(CONTROL_PLANE_WEBHOOK_URL_CONFIG, null);
            controlPlaneProtocolUrl = configuration.getPropertyAsString(CONTROL_PLANE_PROTOCOL_URL_CONFIG, null);
            dataPlaneUrl = configuration.getPropertyAsString(DATA_PLANE_URL_CONFIG, null);
            dataPlaneAuthorization = configuration.getPropertyAsString(DATA_PLANE_AUTHORIZATION_CONFIG, "dummy-authorization");
        }
    }

    @Override
    public <T> boolean providesService(Class<T> type) {
        return type.equals(ControlPlaneSignalingPipeline.class) || type.equals(DataPlaneSignalingPipeline.class);
    }

    @Override
    public <T> T getService(Class<T> type, ServiceConfiguration configuration, ServiceResolver resolver) {
        if (ControlPlaneSignalingPipeline.class.equals(type)) {
            var callbackEndpoint = (CallbackEndpoint) resolver.resolve(CallbackEndpoint.class, configuration);
            var pipeline = useLocalConnector ? localControlPlanePipeline(callbackEndpoint) : httpControlPlanePipeline(callbackEndpoint);
            return type.cast(pipeline);
        }
        if (DataPlaneSignalingPipeline.class.equals(type)) {
            var callbackEndpoint = (CallbackEndpoint) resolver.resolve(CallbackEndpoint.class, configuration);
            var pipeline = useLocalConnector ? localDataPlanePipeline(callbackEndpoint) : httpDataPlanePipeline(callbackEndpoint);
            return type.cast(pipeline);
        }
        return null;
    }

    private @NonNull ControlPlaneSignalingPipeline httpControlPlanePipeline(CallbackEndpoint callbackEndpoint) {
        if (controlPlaneWebhookUrl == null) {
            throw new RuntimeException("Required configuration not set: " + CONTROL_PLANE_WEBHOOK_URL_CONFIG);
        }
        if (controlPlaneProtocolUrl == null) {
            throw new RuntimeException("Required configuration not set: " + CONTROL_PLANE_PROTOCOL_URL_CONFIG);
        }
        var controlPlaneClient = new HttpControlPlaneClient(controlPlaneWebhookUrl, monitor, mapper);
        var dspClient = new HttpDspClient(controlPlaneProtocolUrl, monitor, mapper);
        return new ControlPlaneSignalingPipeline(controlPlaneClient, dspClient, callbackEndpoint, monitor, waitTime, mapper);
    }

    private @NonNull ControlPlaneSignalingPipeline localControlPlanePipeline(CallbackEndpoint callbackEndpoint) {
        var connector = new LocalControlPlaneConnector(monitor);
        var controlPlaneClient = new LocalControlPlaneClient(connector);
        var dspClient = new LocalDspClient(connector);
        return new ControlPlaneSignalingPipeline(controlPlaneClient, dspClient, callbackEndpoint, monitor, waitTime, mapper);
    }

    protected @NonNull DataPlaneSignalingPipeline httpDataPlanePipeline(CallbackEndpoint callbackEndpoint) {
        if (dataPlaneUrl == null) {
            throw new RuntimeException("Required configuration not set: " + DATA_PLANE_URL_CONFIG);
        }
        var dataPlaneClient = new HttpDataPlaneClient(dataPlaneUrl, monitor, mapper, dataPlaneAuthorization);
        return new DataPlaneSignalingPipeline(dataPlaneClient, callbackEndpoint, monitor, waitTime, mapper);
    }

    private @NonNull DataPlaneSignalingPipeline localDataPlanePipeline(CallbackEndpoint callbackEndpoint) {
        var connector = new LocalDataPlaneConnector(monitor);
        var dataPlaneClient = new LocalDataPlaneClient(connector);
        return new DataPlaneSignalingPipeline(dataPlaneClient, callbackEndpoint, monitor, waitTime, mapper);
    }

}

