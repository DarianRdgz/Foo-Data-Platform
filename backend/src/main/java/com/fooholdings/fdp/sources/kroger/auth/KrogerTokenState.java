package com.fooholdings.fdp.sources.kroger.auth;

import java.time.Instant;

/**
 * Immutable snapshot of a Kroger OAuth token.
 *
 * Storing expiry as an Instant is intentional: it is calculated
 * once at fetch time and can be compared cheaply against Instant.now() without
 * any arithmetic in the hot path.
 *
 */
public record KrogerTokenState(String accessToken, Instant expiresAt) {

    private static final long EXPIRY_BUFFER_SECONDS = 60;

    //Returns true if the token has expired or will expire within the buffer window
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt.minusSeconds(EXPIRY_BUFFER_SECONDS));
    }

    /**
     * Factory: builds a KrogerTokenState from a token response.
     * expiresAt = current time plus the expires_in value.
     */
    public static KrogerTokenState from(String accessToken, long expiresInSeconds) {
        return new KrogerTokenState(
                accessToken,
                Instant.now().plusSeconds(expiresInSeconds)
        );
    }
}
