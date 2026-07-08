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

package org.eclipse.dataspacetck.dps.system.crypto;

import com.nimbusds.jwt.JWTClaimsSet;
import org.eclipse.dataspacetck.dcp.system.crypto.KeyService;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static java.util.Collections.emptyMap;

/**
 * Builds the client-authentication bearer token used when the data consumer (client) requests a token
 * refresh, as defined by the Token Renewal profile's Client Authentication section.
 *
 * <p>The token is a JWT signed by the client whose public key is published in the client's DID document.
 * The provider resolves the client DID from the {@code iss} claim, selects the key referenced by the
 * {@code kid} header, and verifies the signature.
 */
public class RefreshTokenAuthenticator {

    private static final long TOKEN_LIFETIME_SECONDS = 600;

    private final KeyService keyService;
    private final String clientDid;
    private final String providerDid;

    public RefreshTokenAuthenticator(KeyService keyService, String clientDid, String providerDid) {
        this.keyService = keyService;
        this.clientDid = clientDid;
        this.providerDid = providerDid;
    }

    /**
     * Creates a signed client-authentication JWT carrying the access token associated with the refresh token.
     *
     * @param accessToken the current access token (the DataAddress {@code authorization} value).
     * @return the serialized, signed JWT to present as a bearer token.
     */
    public String createBearerToken(String accessToken) {
        return sign(clientDid, clientDid, providerDid, accessToken);
    }

    /**
     * Creates a client-authentication JWT with deliberately wrong {@code aud},
     * used by negative tests to verify the provider rejects an invalid client authentication.
     */
    public String createInvalidBearerToken(String accessToken) {
        return sign(clientDid, clientDid, "did:web:invalid-audience", accessToken);
    }

    private String sign(String issuer, String subject, String audience, String accessToken) {
        var now = Instant.now();
        var claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(subject)
                .audience(audience)
                .claim("token", accessToken)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(TOKEN_LIFETIME_SECONDS)))
                .build();
        // Empty headers -> KeyService derives kid = <iss>#<keyId>, matching the DID document verification method.
        return keyService.sign(emptyMap(), claims);
    }
}
