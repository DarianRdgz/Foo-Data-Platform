package com.fooholdings.fdp.geo.service;
 
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
 
import com.fooholdings.fdp.geo.event.AreaIngestCompletedEvent;
 
@Service
public class GeoChangeDetectionService {
 
    private static final Logger log = LoggerFactory.getLogger(GeoChangeDetectionService.class);
 
    private static final String UPSERT_CHANGES_SQL = """
            with ranked_periods as (
                select
                    geo_id,
                    category,
                    snapshot_period,
                    (payload->>'value')::numeric as value,
                    row_number() over (
                        partition by geo_id, category
                        order by snapshot_period desc
                    ) as rn
                from fdp_geo.area_snapshot
                where is_rollup = false
                  and payload->>'value' is not null
                  and (:category is null or category = :category)
            ),
            latest as (
                select * from ranked_periods where rn = 1
            ),
            prior as (
                select * from ranked_periods where rn = 2
            ),
            computed as (
                select
                    l.geo_id,
                    l.category,
                    p.snapshot_period as prior_period,
                    l.snapshot_period as current_period,
                    case
                        when p.value = 0 then 0::numeric
                        else round((l.value - p.value) / abs(p.value) * 100, 6)
                    end as pct_change,
                    case
                        when l.value > p.value then 'up'
                        when l.value < p.value then 'down'
                        else 'flat'
                    end as direction,
                    case
                        when abs(case
                            when p.value = 0 then 0::numeric
                            else (l.value - p.value) / abs(p.value) * 100
                        end) < 1.0 then 'slight'
                        when abs(case
                            when p.value = 0 then 0::numeric
                            else (l.value - p.value) / abs(p.value) * 100
                        end) < 5.0 then 'moderate'
                        else 'significant'
                    end as magnitude
                from latest l
                join prior p
                  on p.geo_id = l.geo_id
                 and p.category = l.category
            )
            insert into fdp_geo.area_change_log
                (geo_id, category, prior_period, current_period, pct_change, direction, magnitude)
            select
                geo_id,
                category,
                prior_period,
                current_period,
                pct_change,
                direction::fdp_geo.change_direction,
                magnitude
            from computed
            on conflict (geo_id, category, current_period) do update set
                prior_period = excluded.prior_period,
                pct_change   = excluded.pct_change,
                direction    = excluded.direction,
                magnitude    = excluded.magnitude,
                computed_at  = now()
            """;
 
    private final NamedParameterJdbcTemplate jdbc;
 
    public GeoChangeDetectionService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }
 
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIngestCompleted(AreaIngestCompletedEvent event) {
        log.info("[change-detection] Triggered by sourceCode={} category={} rowsWritten={}",
                event.getSourceCode(), event.getCategory(), event.getRowsWritten());
        computeChanges(event.getCategory());
    }
 
    public int computeChanges(String categoryFilter) {
        var params = new MapSqlParameterSource("category", categoryFilter);
        int rows = jdbc.update(UPSERT_CHANGES_SQL, params);
        log.info("[change-detection] Wrote/updated {} area_change_log rows (category={})", rows, categoryFilter);
        return rows;
    }
 
    public int computeAll() {
        return computeChanges(null);
    }
}
