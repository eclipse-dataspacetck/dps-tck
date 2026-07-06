/*
 *  Copyright (c) 2026 Metaform Systems, Inc.
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

package org.eclipse.dataspacetck.dps.system.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds {@code DataAddress} representations that conform to the HTTP transfer profile.
 *
 * <p>See the HTTP profile (specifications/profiles/http.md) and the Token Renewal profile
 * (specifications/profiles/token-renewal.md) of the Dataplane Signaling Protocol.
 */
public final class HttpDataAddresses {

    /**
     * The {@code endpointType} constant identifying an HTTP pull data address.
     */
    public static final String HTTP_PULL = "https://w3id.org/dspace-sig/profile/http-pull";

    /**
     * The {@code endpointType} constant identifying an HTTP push data address.
     */
    public static final String HTTP_PUSH = "https://w3id.org/dspace-sig/profile/http-push";

    public static final String AUTHORIZATION = "authorization";
    public static final String AUTH_TYPE = "authType";
    public static final String BEARER = "bearer";
    public static final String REFRESH_TOKEN = "refreshToken";
    public static final String EXPIRES_IN = "expiresIn";
    public static final String REFRESH_ENDPOINT = "refreshEndpoint";

    private HttpDataAddresses() {
    }

    /**
     * Builds a valid HTTP pull data address carrying a bearer authorization token.
     */
    public static Map<String, Object> httpPull(String endpoint, String token) {
        return dataAddress(HTTP_PULL, endpoint, bearerProperties(token));
    }

    /**
     * Builds a valid HTTP push data address carrying a bearer authorization token.
     */
    public static Map<String, Object> httpPush(String endpoint, String token) {
        return dataAddress(HTTP_PUSH, endpoint, bearerProperties(token));
    }

    /**
     * Builds a valid HTTP pull data address that additionally carries the Token Renewal profile
     * properties ({@code refreshToken}, {@code expiresIn}, {@code refreshEndpoint}).
     */
    public static Map<String, Object> httpPullWithRenewal(String endpoint, String token, String refreshToken,
                                                          String expiresIn, String refreshEndpoint) {
        var properties = bearerProperties(token);
        properties.add(property(REFRESH_TOKEN, refreshToken));
        properties.add(property(EXPIRES_IN, expiresIn));
        properties.add(property(REFRESH_ENDPOINT, refreshEndpoint));
        return dataAddress(HTTP_PULL, endpoint, properties);
    }

    private static List<Map<String, Object>> bearerProperties(String token) {
        var properties = new ArrayList<Map<String, Object>>();
        properties.add(property(AUTHORIZATION, token));
        properties.add(property(AUTH_TYPE, BEARER));
        return properties;
    }

    private static Map<String, Object> property(String name, String value) {
        return Map.of("name", name, "value", value);
    }

    private static Map<String, Object> dataAddress(String endpointType, String endpoint, List<Map<String, Object>> properties) {
        return Map.of(
                "@type", "DataAddress",
                "endpointType", endpointType,
                "endpoint", endpoint,
                "endpointProperties", properties
        );
    }
}
