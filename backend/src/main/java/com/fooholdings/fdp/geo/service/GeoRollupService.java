package com.fooholdings.fdp.geo.service;
 
import java.util.List;
 
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
 
import com.fooholdings.fdp.geo.event.AreaIngestCompletedEvent;
 
@Service
public class GeoRollupService {
 
    private static final Logger log = LoggerFactory.getLogger(GeoRollupService.class);
 
    static final String SOURCE_LABEL = "ROLLUP";
 
    record RollupSpec(String category, String childLevel, String parentLevel) {}
 
    private static final List<RollupSpec> ROLLUP_SPECS = List.of(
            new RollupSpec("housing.home_value", "zip",    "city"),
            new RollupSpec("housing.home_value", "city",   "county"),
            new RollupSpec("housing.home_value", "county", "metro"),
            new RollupSpec("housing.home_value", "metro",  "state"),
            new RollupSpec("housing.rent_index", "zip",    "city"),
            new RollupSpec("housing.rent_index", "city",   "county"),
            new RollupSpec("housing.rent_index", "county", "metro"),
            new RollupSpec("housing.rent_index", "metro",  "state")
    );
 
    private static final String ROLLUP_UPSERT_SQL = """
            with child_rows as (
                select
                    s.geo_id as child_geo_id,
                    s.category,
                    s.snapshot_period,
                    (s.payload->>'value')::numeric as child_value,
                    coalesce(c.rollup_weight, 1.0) as child_weight
                from fdp_geo.area_snapshot s
                join fdp_geo.geo_areas c
                  on c.geo_id = s.geo_id
                where s.category = :category
                  and s.is_rollup = false
                  and c.geo_level = :childLevel
                  and s.payload->>'value' is not null
            ),
            rollup_candidates as (
                select
                    p.geo_id as parent_geo_id,
                    cr.category,
                    cr.snapshot_period,
                    round(
                        sum(cr.child_value * cr.child_weight) / nullif(sum(cr.child_weight), 0),
                        6
                    ) as rollup_value,
                    count(*) as child_count,
                    sum(cr.child_weight) as total_weight
                from fdp_geo.geo_areas p
                join fdp_geo.geo_areas c
                  on c.parent_geo_id = p.geo_id
                join child_rows cr
                  on cr.child_geo_id = c.geo_id
                where p.geo_level = :parentLevel
                  and c.geo_level = :childLevel
                group by p.geo_id, cr.category, cr.snapshot_period
            ),
            eligible as (
                select rc.*
                from rollup_candidates rc
                where not exists (
                    select 1
                    from fdp_geo.area_snapshot s
                    where s.geo_id = rc.parent_geo_id
                      and s.category = rc.category
                      and s.snapshot_period = rc.snapshot_period
                      and s.is_rollup = false
                )
            )
            insert into fdp_geo.area_snapshot
                (geo_id, category, snapshot_period, source, is_rollup, payload)
            select
                parent_geo_id,
                category,
                snapshot_period,
                cast(:sourceLabel as text) as source,
                true as is_rollup,
                jsonb_build_object(
                    'value', rollup_value,
                    'childCount', child_count,
                    'weightSum', total_weight,
                    'method', 'weighted_average',
                    'weightColumn', 'rollup_weight'
                ) as payload
            from eligible
            on conflict (geo_id, category, snapshot_period, source) do update set
                payload = excluded.payload,
                is_rollup = true,
                ingested_at = now()
            """;
 
    private final NamedParameterJdbcTemplate jdbc;
 
    public GeoRollupService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }
 
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIngestCompleted(AreaIngestCompletedEvent event) {
        log.info("[rollup] Triggered by sourceCode={} category={} rowsWritten={}",
                event.getSourceCode(), event.getCategory(), event.getRowsWritten());
 
        List<RollupSpec> specs = event.getCategory() == null
                ? ROLLUP_SPECS
                : ROLLUP_SPECS.stream()
                    .filter(spec -> spec.category().equals(event.getCategory()))
                    .toList();
 
        int total = 0;
        for (RollupSpec spec : specs) {
            total += applyRollup(spec);
        }
 
        log.info("[rollup] Done: {} row(s) written/updated across {} spec(s)", total, specs.size());
    }
 
    public int rollupAll() {
        int total = 0;
        for (RollupSpec spec : ROLLUP_SPECS) {
            total += applyRollup(spec);
        }
        log.info("[rollup] Full sweep complete: {} row(s) total", total);
        return total;
    }
 
    public int rollupCategory(String category) {
        return ROLLUP_SPECS.stream()
                .filter(spec -> spec.category().equals(category))
                .mapToInt(this::applyRollup)
                .sum();
    }
 
    private int applyRollup(RollupSpec spec) {
        var params = new MapSqlParameterSource()
                .addValue("sourceLabel", SOURCE_LABEL)
                .addValue("category", spec.category())
                .addValue("childLevel", spec.childLevel())
                .addValue("parentLevel", spec.parentLevel());
 
        int rows = jdbc.update(ROLLUP_UPSERT_SQL, params);
        if (rows > 0) {
            log.debug("[rollup] {} rows: category={} {}→{}",
                    rows, spec.category(), spec.childLevel(), spec.parentLevel());
        }
        return rows;
    }
}
