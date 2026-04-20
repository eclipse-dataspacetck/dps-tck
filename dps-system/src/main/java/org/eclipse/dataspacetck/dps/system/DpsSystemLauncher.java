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

import org.eclipse.dataspacetck.core.api.system.CallbackEndpoint;
import org.eclipse.dataspacetck.core.spi.boot.Monitor;
import org.eclipse.dataspacetck.core.spi.system.ServiceConfiguration;
import org.eclipse.dataspacetck.core.spi.system.ServiceResolver;
import org.eclipse.dataspacetck.core.spi.system.SystemConfiguration;
import org.eclipse.dataspacetck.core.spi.system.SystemLauncher;
import org.eclipse.dataspacetck.dps.system.api.pipeline.ControlPlaneSignalingPipeline;
import org.eclipse.dataspacetck.dps.system.client.HttpControlPlaneClient;
import org.eclipse.dataspacetck.dps.system.pipeline.ControlPlaneSignalingPipelineImpl;

import static org.eclipse.dataspacetck.core.api.system.SystemsConstants.TCK_PREFIX;

/**
 * Instantiates and bootstraps a DPS test fixture. The test fixture consists of immutable base services and services which
 * are instantiated per test.
 */
public class DpsSystemLauncher implements SystemLauncher {

    private static final String CONTROL_PLANE_URL_CONFIG = TCK_PREFIX + ".dps.controlplane.url";
    private static final int DEFAULT_WAIT_SECONDS = 15;
    private static final String DEFAULT_WAIT_CONFIG = TCK_PREFIX + ".dps.default.wait";

    private String controlPlaneUrl;
    private long waitTime = DEFAULT_WAIT_SECONDS;
    private Monitor monitor;

    @Override
    public void start(SystemConfiguration configuration) {
        monitor = configuration.getMonitor();
        waitTime = configuration.getPropertyAsLong(DEFAULT_WAIT_CONFIG, DEFAULT_WAIT_SECONDS);
        controlPlaneUrl = configuration.getPropertyAsString(CONTROL_PLANE_URL_CONFIG, null);
        if (controlPlaneUrl == null) {
            throw new RuntimeException("Required configuration not set: " + CONTROL_PLANE_URL_CONFIG);
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
            var client = new HttpControlPlaneClient(controlPlaneUrl, monitor);
            return type.cast(new ControlPlaneSignalingPipelineImpl(client, callbackEndpoint, monitor, waitTime));
        }
        return null;
    }

    @Override
    public void beforeExecution(ServiceConfiguration configuration, ServiceResolver resolver) {
    }

}
