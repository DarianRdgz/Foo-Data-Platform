package com.fooholdings.fdp.core.ingestion;

import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.fooholdings.fdp.api.dto.HealthResponse;

class IngestionHealthServiceTest {

    @Test
    void returnsSourceRowEvenWhenNoRunsExist() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        IngestionHealthService svc = new IngestionHealthService(jdbc);

        when(jdbc.query(
            anyString(),
            ArgumentMatchers.<RowMapper<HealthResponse.SourceIngestionStatus>>any()))
        .thenAnswer(inv -> {
            RowMapper<HealthResponse.SourceIngestionStatus> mapper =
                    inv.getArgument(1);

            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("source")).thenReturn("KROGER");
            when(rs.getObject("run_id")).thenReturn(null);
            when(rs.getString("status")).thenReturn(null);
            when(rs.getTimestamp("started_at")).thenReturn(null);
            when(rs.getTimestamp("finished_at")).thenReturn(null);
            when(rs.getString("message")).thenReturn(null);

            return List.of(mapper.mapRow(rs, 0));
        });

        HealthResponse resp = svc.getHealth();
        assertNotNull(resp.generatedAt());
        assertEquals(1, resp.sources().size());
        assertEquals("KROGER", resp.sources().get(0).source());
        assertNull(resp.sources().get(0).lastRunId());
    }
}