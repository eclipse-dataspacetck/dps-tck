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
import org.eclipse.dataspacetck.dcp.system.crypto.KeyServiceImpl;
import org.eclipse.dataspacetck.dcp.system.crypto.Keys;
import org.eclipse.dataspacetck.dcp.system.did.DidDocumentHandler;
import org.eclipse.dataspacetck.dcp.system.did.DidServiceImpl;
import org.eclipse.dataspacetck.dps.system.client.http.HttpControlPlaneClient;
import org.eclipse.dataspacetck.dps.system.client.http.HttpDataPlaneClient;
import org.eclipse.dataspacetck.dps.system.client.http.HttpDspClient;
import org.eclipse.dataspacetck.dps.system.client.local.LocalControlPlaneClient;
import org.eclipse.dataspacetck.dps.system.client.local.LocalDataPlaneClient;
import org.eclipse.dataspacetck.dps.system.client.local.LocalDspClient;
import org.eclipse.dataspacetck.dps.system.connector.LocalControlPlaneConnector;
import org.eclipse.dataspacetck.dps.system.connector.LocalDataPlaneConnector;
import org.eclipse.dataspacetck.dps.system.crypto.RefreshTokenAuthenticator;
import org.eclipse.dataspacetck.dps.system.pipeline.ControlPlaneSignalingPipeline;
import org.eclipse.dataspacetck.dps.system.pipeline.DataPlaneSignalingPipeline;
import org.jspecify.annotations.NonNull;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;

import static org.eclipse.dataspacetck.core.api.system.SystemsConstants.TCK_PREFIX;

/**
 * Instantiates and bootstraps a DPS test fixture. The test fixture consists of immutable base services and services which
 * are instantiated per test.
 */
public class DpsSystemLauncher implements SystemLauncher {

    private static final String LOCAL_CONNECTOR_CONFIG = TCK_PREFIX + ".dps.local.connector";
    private static final String CONTROL_PLANE_WEBHOOK_URL_CONFIG = TCK_PREFIX + ".dps.controlplane.webhook.url";
    private static final String CONTROL_PLANE_PROTOCOL_URL_CONFIG = TCK_PREFIX + ".dps.controlplane.protocol.url";
    private static final String CONTROL_PLANE_SIGNALING_URL_CONFIG = TCK_PREFIX + ".dps.controlplane.signaling.url";
    private static final String DATA_PLANE_URL_CONFIG = TCK_PREFIX + ".dps.dataplane.url";
    private static final String DATA_PLANE_AUTHORIZATION_CONFIG = TCK_PREFIX + ".dps.dataplane.authorization";
    private static final String PROVIDER_ID = TCK_PREFIX + ".dps.dataplane.provider.id";
    private static final String COUNTER_PARTY_ID = TCK_PREFIX + ".dps.dataplane.counterparty.id";
    private static final int DEFAULT_WAIT_SECONDS = 15;
    private static final String DEFAULT_WAIT_CONFIG = TCK_PREFIX + ".dps.default.wait";
    private static final String DID_DOCUMENT_PATH = "/\\.well-known/did\\.json";
    private final ObjectMapper mapper = new ObjectMapper();
    private String controlPlaneWebhookUrl;
    private String controlPlaneProtocolUrl;
    private String controlPlaneSignalingUrl;
    private String dataPlaneUrl;
    private String dataPlaneAuthorization;
    private String providerId;
    private String counterPartyId;
    private long waitTime = DEFAULT_WAIT_SECONDS;
    private boolean useLocalConnector;
    private Monitor monitor;

    /**
     * Derives a {@code did:web} identifier from an HTTP(S) URL, encoding a non-default port as {@code %3A}.
     * The corresponding DID document is resolved at {@code <host>[:<port>]/.well-known/did.json}.
     */
    private static String toDidWeb(String url) {
        var uri = URI.create(url);
        var port = uri.getPort();
        return port > 0 && port != 443
                ? "did:web:%s%%3A%s".formatted(uri.getHost(), port)
                : "did:web:%s".formatted(uri.getHost());
    }

    @Override
    public void start(SystemConfiguration configuration) {
        monitor = configuration.getMonitor();
        waitTime = configuration.getPropertyAsLong(DEFAULT_WAIT_CONFIG, DEFAULT_WAIT_SECONDS);
        useLocalConnector = configuration.getPropertyAsBoolean(LOCAL_CONNECTOR_CONFIG, false);
        // Read regardless of mode: the local pipeline also needs a provider identity for the token-renewal flow.
        providerId = configuration.getPropertyAsString(PROVIDER_ID, "tck-participant");
        counterPartyId = configuration.getPropertyAsString(COUNTER_PARTY_ID, "tck-counterparty");
        if (!useLocalConnector) {
            controlPlaneWebhookUrl = configuration.getPropertyAsString(CONTROL_PLANE_WEBHOOK_URL_CONFIG, null);
            controlPlaneProtocolUrl = configuration.getPropertyAsString(CONTROL_PLANE_PROTOCOL_URL_CONFIG, null);
            controlPlaneSignalingUrl = configuration.getPropertyAsString(CONTROL_PLANE_SIGNALING_URL_CONFIG, null);
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
        if (controlPlaneSignalingUrl == null) {
            throw new RuntimeException("Required configuration not set: " + CONTROL_PLANE_SIGNALING_URL_CONFIG);
        }
        var controlPlaneClient = new HttpControlPlaneClient(controlPlaneWebhookUrl, controlPlaneSignalingUrl, monitor, mapper);
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
        // Acting as the token-renewal data client: generate a key, publish it via a did:web document on the
        // TCK callback endpoint, and sign refresh requests so the provider can resolve and verify the client JWT.
        var keyService = new KeyServiceImpl(Keys.generateEcKey());
        var clientDid = toDidWeb(callbackEndpoint.getAddress());
        var didService = new DidServiceImpl(clientDid, callbackEndpoint.getAddress(), keyService);
        callbackEndpoint.registerHandler(DID_DOCUMENT_PATH, new DidDocumentHandler(didService, new com.fasterxml.jackson.databind.ObjectMapper()));

        var authenticator = new RefreshTokenAuthenticator(keyService, clientDid, providerId);
        var dataPlaneClient = new HttpDataPlaneClient(providerId, counterPartyId, dataPlaneUrl, monitor, mapper, dataPlaneAuthorization, authenticator);
        return new DataPlaneSignalingPipeline(dataPlaneClient, callbackEndpoint, monitor, waitTime, mapper);
    }

    private @NonNull DataPlaneSignalingPipeline localDataPlanePipeline(CallbackEndpoint callbackEndpoint) {
        // Mirror the http wiring so the in-process connector can validate the token-renewal client authentication:
        // the authenticator signs with clientDid/providerDid and the connector rejects anything that does not match.
        var keyService = new KeyServiceImpl(Keys.generateEcKey());
        var clientDid = toDidWeb(callbackEndpoint.getAddress());
        var authenticator = new RefreshTokenAuthenticator(keyService, clientDid, providerId);
        var connector = new LocalDataPlaneConnector(monitor, clientDid, providerId);
        var dataPlaneClient = new LocalDataPlaneClient(connector, callbackEndpoint, authenticator);
        return new DataPlaneSignalingPipeline(dataPlaneClient, callbackEndpoint, monitor, waitTime, mapper);
    }

}

