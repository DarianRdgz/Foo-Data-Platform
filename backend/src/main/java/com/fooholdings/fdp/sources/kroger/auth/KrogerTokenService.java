package com.fooholdings.fdp.sources.kroger.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe cache for the Kroger OAuth access token.
 *
 * Pattern: lazy refresh with ReentrantLock double-checked locking.
 *
 * Why ReentrantLock over synchronized:
 *   ReentrantLock gives explicit lock/unlock control in a try/finally block,
 *   preventing lock leaks if fetchToken() throws. It also allows tryLock()
 *   for non-blocking refresh in the future
 *
 */
@Service
public class KrogerTokenService {

    private static final Logger log = LoggerFactory.getLogger(KrogerTokenService.class);

    private final KrogerAuthClient authClient;
    private final ReentrantLock lock = new ReentrantLock();

    // Volatile so reads outside the lock see the latest reference
    private volatile KrogerTokenState currentToken;

    public KrogerTokenService(KrogerAuthClient authClient) {
        this.authClient = authClient;
    }

    /**
     * Returns a valid access token, refreshing if expired.
     * Thread-safe. Blocks briefly if a refresh is already in progress.
     *
     * @throws org.springframework.web.client.RestClientException if token fetch fails
     */
    public String getValidToken() {
        // Fast path: read volatile without acquiring lock
        KrogerTokenState snapshot = currentToken;
        if (snapshot != null && !snapshot.isExpired()) {
            return snapshot.accessToken();
        }

        // Slow path: lock and check again
        lock.lock();
        try {
            snapshot = currentToken;
            if (snapshot != null && !snapshot.isExpired()) {
                return snapshot.accessToken();
            }
            log.info("Kroger token expired or absent — fetching new token");
            var response = authClient.fetchToken();
            currentToken = KrogerTokenState.from(response.getAccessToken(), response.getExpiresIn());
            log.info("Kroger token refreshed — expires at {}", currentToken.expiresAt());
            return currentToken.accessToken();
        } finally {
            lock.unlock();
        }
    }

    // Forces a token refresh regardless of expiry
    public String forceRefresh() {
        lock.lock();
        try {
            log.info("Kroger token force-refresh triggered");
            var response = authClient.fetchToken();
            currentToken = KrogerTokenState.from(response.getAccessToken(), response.getExpiresIn());
            return currentToken.accessToken();
        } finally {
            lock.unlock();
        }
    }

    // Returns current token state, null if no token has been fetched yet
    public KrogerTokenState getCurrentState() {
        return currentToken;
    }
}
