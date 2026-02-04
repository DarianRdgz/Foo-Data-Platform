package com.fooholdings.fdp.kroger.auth;

import java.time.Instant;

final class KrogerTokenState {

    private final String accessToken;
    private final Instant expiresAt;
    private final Instant lastRefresh;

    KrogerTokenState(String accessToken, Instant expiresAt, Instant lastRefresh) {
        this.accessToken = accessToken;
        this.expiresAt = expiresAt;
        this.lastRefresh = lastRefresh;
    }

    String getAccessToken() {
        return accessToken;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    Instant getLastRefresh() {
        return lastRefresh;
    }
}
