package com.fooholdings.fdp.core.ingestion;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fooholdings.fdp.core.source.SourceSystemService;

@ExtendWith(MockitoExtension.class)
class IngestionLockServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private SourceSystemService sourceSystemService;

    private IngestionLockService lockService;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        when(sourceSystemService.getRequiredIdByCode("KROGER")).thenReturn((short) 1);
        lockService = new IngestionLockService(jdbc, sourceSystemService);
    }

    @Test
    void tryAcquire_returnsTrueWhenJdbcUpdatesOneRow() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        boolean acquired = lockService.tryAcquire("KROGER", "fdp-backend", Duration.ofMinutes(20));

        assertThat(acquired).isTrue();
    }

    @Test
    void tryAcquire_returnsFalseWhenJdbcUpdatesZeroRows() {
        // ON CONFLICT DO UPDATE WHERE is false — lock already held by another process
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);

        boolean acquired = lockService.tryAcquire("KROGER", "fdp-backend", Duration.ofMinutes(20));

        assertThat(acquired).isFalse();
    }

    @Test
    void release_executesDeleteWithCorrectParameters() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        lockService.release("KROGER", "fdp-backend");

        verify(jdbc).update(contains("DELETE FROM fdp_core.ingestion_lock"),
                eq((short) 1), eq("fdp-backend"));
    }

    @Test
    void release_isNoOpWhenLockNotOwned() {
        // Returns 0 — lock not found or owned by someone else
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);

        // Should not throw
        lockService.release("KROGER", "different-owner");

        verify(jdbc).update(anyString(), any(Object[].class));
    }
}
