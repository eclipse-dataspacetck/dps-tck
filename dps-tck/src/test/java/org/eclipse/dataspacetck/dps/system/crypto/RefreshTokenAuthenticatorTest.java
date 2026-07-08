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

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.eclipse.dataspacetck.dcp.system.crypto.KeyService;
import org.eclipse.dataspacetck.dcp.system.crypto.KeyServiceImpl;
import org.eclipse.dataspacetck.dcp.system.crypto.Keys;
import org.eclipse.dataspacetck.dcp.system.did.DidServiceImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the token-renewal client-authentication JWT is produced with the shape mandated by the
 * Token Renewal profile and can be verified against the key published in the client's DID document.
 */
class RefreshTokenAuthenticatorTest {

    private static final String CLIENT_DID = "did:web:tck-client.example.com";
    private static final String PROVIDER_DID = "did:web:provider.example.com";

    @Test
    void createBearerToken_producesSignedJwtVerifiableViaDidDocument() throws Exception {
        var accessToken = "ACCESS-XYZ";
        KeyService keyService = new KeyServiceImpl(Keys.generateEcKey());
        var didService = new DidServiceImpl(CLIENT_DID, "https://tck-client.example.com", keyService);
        var authenticator = new RefreshTokenAuthenticator(keyService, CLIENT_DID, PROVIDER_DID);

        var parsed = SignedJWT.parse(authenticator.createBearerToken(accessToken));
        var expectedKid = CLIENT_DID + "#" + keyService.getPublicKey().getKeyID();

        // header
        assertThat(parsed.getHeader().getKeyID()).isEqualTo(expectedKid);
        assertThat(parsed.getHeader().getAlgorithm().getName()).isEqualTo("ES256");

        // claims per the Client Authentication section
        var claims = parsed.getJWTClaimsSet();
        assertThat(claims.getIssuer()).isEqualTo(CLIENT_DID);
        assertThat(claims.getSubject()).isEqualTo(CLIENT_DID);
        assertThat(claims.getAudience()).containsExactly(PROVIDER_DID);
        assertThat(claims.getStringClaim("token")).isEqualTo(accessToken);
        assertThat(claims.getJWTID()).isNotBlank();
        assertThat(claims.getExpirationTime()).isAfter(claims.getIssueTime());

        // signature verifies against the signing key
        JWSVerifier verifier = Keys.createVerifier(keyService.getPublicKey().toECKey().toECPublicKey());
        assertThat(parsed.verify(verifier)).isTrue();

        // the DID document publishes the key referenced by the JWT kid
        var document = didService.resolveDidDocument();
        assertThat(document.succeeded()).isTrue();
        var verificationMethod = document.getContent().getVerificationMethod(expectedKid);
        assertThat(verificationMethod.succeeded()).isTrue();
        assertThat(verificationMethod.getContent().getPublicKeyJwk()).isEqualTo(keyService.getPublicKey().toJSONObject());
    }
}
