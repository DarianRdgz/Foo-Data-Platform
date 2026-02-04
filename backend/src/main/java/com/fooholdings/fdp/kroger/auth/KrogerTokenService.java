package com.fooholdings.fdp.kroger.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fooholdings.fdp.kroger.auth.dto.KrogerTokenResponse;

@Service
public class KrogerTokenService {

    private static final Logger log = LoggerFactory.getLogger(KrogerTokenService.class);

    /*
     * Refresh buffer to avoid token expiring mid-request
     * If token expires within this window, refresh
     */
    private static final Duration REFRESH_BUFFER = Duration.ofSeconds(60);

    private final KrogerAuthClient authClient;

    // Volatile so readers see the latest state without locking
    private volatile KrogerTokenState state;

    // Lock so only one thread refreshes at a time
    private final ReentrantLock refreshLock = new ReentrantLock();

    public KrogerTokenService(KrogerAuthClient authClient) {
        this.authClient = authClient;
    }

    /*
     * Returns a valid access token. Uses cached token if still valid.
     * Refreshes token if missing, expired, or within refresh buffer.
     */
    public String getAccessToken() {
        KrogerTokenState snapshot = state;

        if (snapshot != null && isStillValid(snapshot, Instant.now())) {
            return snapshot.getAccessToken();
        }

        // Slow path: refresh under lock
        refreshLock.lock();
        try {
            // Double-check after acquiring lock (another thread may have refreshed)
            snapshot = state;
            if (snapshot != null && isStillValid(snapshot, Instant.now())) {
                return snapshot.getAccessToken();
            }

            KrogerTokenResponse tokenResponse = authClient.requestClientCredentialsToken();

            Instant now = Instant.now();
            Instant expiresAt = now.plusSeconds(tokenResponse.getExpiresIn());

            state = new KrogerTokenState(tokenResponse.getAccessToken(), expiresAt, now);

            // Safe log: no token printed
            log.info("Kroger token cached: expiresAt={}", expiresAt);

            return state.getAccessToken();

        } finally {
            refreshLock.unlock();
        }
    }

    /*
     * Returns metadata for status endpoint
     */
    public TokenStatus getStatus() {
        KrogerTokenState snapshot = state;
        Instant now = Instant.now();

        if (snapshot == null) {
            return new TokenStatus(false, null, null, null);
        }

        long expiresInSeconds = Duration.between(now, snapshot.getExpiresAt()).getSeconds();
        if (expiresInSeconds < 0) expiresInSeconds = 0;

        return new TokenStatus(
                true,
                snapshot.getExpiresAt(),
                expiresInSeconds,
                snapshot.getLastRefresh()
        );
    }

    private boolean isStillValid(KrogerTokenState snapshot, Instant now) {
        // Valid if expiresAt is after (now + buffer)
        return snapshot.getExpiresAt().isAfter(now.plus(REFRESH_BUFFER));
    }

    /**
     * Small DTO for internal status reporting (safe fields only).
     */
    public record TokenStatus(
            boolean hasToken,
            Instant expiresAt,
            Long expiresInSeconds,
            Instant lastRefresh
    ) {}
}
