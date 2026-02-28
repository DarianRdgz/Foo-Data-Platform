package com.fooholdings.fdp.sources.kroger.auth;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for KrogerTokenState
 */
class KrogerTokenStateTest {

    @Test
    void freshToken_isNotExpired() {
        KrogerTokenState token = KrogerTokenState.from("abc123", 1800L);
        assertThat(token.isExpired()).isFalse();
    }

    @Test
    void expiredToken_isExpired() {
        // expiresAt in the past
        KrogerTokenState token = new KrogerTokenState("abc123", Instant.now().minusSeconds(10));
        assertThat(token.isExpired()).isTrue();
    }

    @Test
    void tokenWithinBufferWindow_isConsideredExpired() {
        // 30 seconds left — within the 60-second buffer, should be treated as expired
        KrogerTokenState token = new KrogerTokenState("abc123", Instant.now().plusSeconds(30));
        assertThat(token.isExpired()).isTrue();
    }

    @Test
    void tokenJustOutsideBuffer_isNotExpired() {
        // 90 seconds left — outside the 60-second buffer
        KrogerTokenState token = new KrogerTokenState("abc123", Instant.now().plusSeconds(90));
        assertThat(token.isExpired()).isFalse();
    }

    @Test
    void from_calculatesExpiryFromNow() {
        long expiresIn = 1800L;
        Instant before = Instant.now();
        KrogerTokenState token = KrogerTokenState.from("tok", expiresIn);
        Instant after = Instant.now();

        assertThat(token.expiresAt()).isAfterOrEqualTo(before.plusSeconds(expiresIn));
        assertThat(token.expiresAt()).isBeforeOrEqualTo(after.plusSeconds(expiresIn));
    }
}
