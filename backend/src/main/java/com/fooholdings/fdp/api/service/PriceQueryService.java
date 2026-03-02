package com.fooholdings.fdp.api.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Array;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;         
import org.slf4j.LoggerFactory;
import static org.springframework.http.HttpStatus.BAD_REQUEST;          
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.fooholdings.fdp.api.dto.PriceResponse;
import com.fooholdings.fdp.api.dto.StapleZipBasketResponse;
import com.fooholdings.fdp.api.dto.TrendResponse;
import com.fooholdings.fdp.core.source.SourceSystemService;

@Service
public class PriceQueryService {

    private static final Logger log = LoggerFactory.getLogger(PriceQueryService.class);

    private static final int HISTORY_MAX_ROWS = 5_000;

    private final NamedParameterJdbcTemplate jdbc;
    private final SourceSystemService sourceSystemService;
    private final Where2MoveStaplesProperties staplesProps;

    public PriceQueryService(NamedParameterJdbcTemplate jdbc,
                             SourceSystemService sourceSystemService,
                             Where2MoveStaplesProperties staplesProps) {
        this.jdbc = jdbc;
        this.sourceSystemService = sourceSystemService;
        this.staplesProps = staplesProps;
    }

    // -----------------------------
    // Product price history
    // -----------------------------
    public List<PriceResponse> getPriceHistory(UUID productId,
                                               UUID locationId,
                                               String zipCode,
                                               Instant fromInclusive,
                                               Instant toExclusive,
                                               String source) {

        Range range = normalizeRange(fromInclusive, toExclusive, null);
        Short sourceSystemId = normalizeSourceSystemId(source);

        String sql = """
            SELECT
                po.source_product_pk AS source_product_id,
                po.store_location_id,
                po.source_system_id,
                po.observed_at,
                po.currency_code,
                po.price,
                po.regular_price,
                po.promo_price,
                po.is_on_sale
            FROM fdp_grocery.price_observation po
            JOIN fdp_grocery.store_location sl
              ON sl.id = po.store_location_id
            WHERE po.source_product_pk = :productId
              AND (:locationId IS NULL OR po.store_location_id = :locationId)
              AND (:zipCode IS NULL OR sl.postal_code = :zipCode)
              AND po.observed_at >= :fromTs
              AND po.observed_at <  :toTs
              AND (:sourceSystemId IS NULL OR po.source_system_id = :sourceSystemId)
            ORDER BY po.observed_at ASC
            LIMIT :limit
            """;

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("productId", productId)
                .addValue("locationId", locationId)
                .addValue("zipCode", blankToNull(zipCode))
                .addValue("fromTs", Timestamp.from(range.fromInclusive()))
                .addValue("toTs", Timestamp.from(range.toExclusive()))
                .addValue("sourceSystemId", sourceSystemId)
                .addValue("limit", HISTORY_MAX_ROWS);

        return jdbc.query(sql, p, (rs, rowNum) -> {
            short ssid = rs.getShort("source_system_id");
            String code = sourceSystemService.getRequiredCodeById(ssid);

            return new PriceResponse(
                    (UUID) rs.getObject("source_product_id"),
                    (UUID) rs.getObject("store_location_id"),
                    code,
                    rs.getTimestamp("observed_at").toInstant(),
                    rs.getString("currency_code"),
                    rs.getBigDecimal("price"),
                    rs.getBigDecimal("regular_price"),
                    rs.getBigDecimal("promo_price"),
                    (Boolean) rs.getObject("is_on_sale")
            );
        });
    }

    // -----------------------------
    // Product trend buckets
    // -----------------------------
    public TrendResponse getProductTrend(UUID productId,
                                         String period,
                                         String zipCode,
                                         String source) {

        Duration d = normalizePeriod(period); // defaults to 30d when null/blank
        Instant toExclusive = Instant.now();
        Instant fromInclusive = toExclusive.minus(d);
        Range range = normalizeRange(fromInclusive, toExclusive, null);
        Short sourceSystemId = normalizeSourceSystemId(source);

        String sql = """
            SELECT
                date_trunc('day', po.observed_at) AS bucket_start,
                date_trunc('day', po.observed_at) + interval '1 day' AS bucket_end,
                MIN(po.price) AS min_price,
                MAX(po.price) AS max_price,
                AVG(po.price) AS avg_price,
                COUNT(*) AS sample_size
            FROM fdp_grocery.price_observation po
            JOIN fdp_grocery.store_location sl
              ON sl.id = po.store_location_id
            WHERE po.source_product_pk = :productId
              AND (:zipCode IS NULL OR sl.postal_code = :zipCode)
              AND po.observed_at >= :fromTs
              AND po.observed_at <  :toTs
              AND (:sourceSystemId IS NULL OR po.source_system_id = :sourceSystemId)
            GROUP BY 1,2
            ORDER BY 1 ASC
            """;

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("productId", productId)
                .addValue("zipCode", blankToNull(zipCode))
                .addValue("fromTs", Timestamp.from(range.fromInclusive()))
                .addValue("toTs", Timestamp.from(range.toExclusive()))
                .addValue("sourceSystemId", sourceSystemId);

        List<TrendResponse.TrendPoint> points = jdbc.query(sql, p, (rs, rowNum) -> {
            BigDecimal avg = rs.getBigDecimal("avg_price");
            if (avg != null) avg = avg.setScale(2, RoundingMode.HALF_UP);

            return new TrendResponse.TrendPoint(
                    rs.getTimestamp("bucket_start").toInstant(),
                    rs.getTimestamp("bucket_end").toInstant(),
                    rs.getBigDecimal("min_price"),
                    rs.getBigDecimal("max_price"),
                    avg,
                    rs.getLong("sample_size")
            );
        });

        return new TrendResponse((period == null || period.isBlank()) ? "30d" : period, points);
    }

    // -------------------------------------------------------------------
    // (Where2Move): Staples basket avg price per ZIP (Cost-of-living)
    // -------------------------------------------------------------------
    public List<StapleZipBasketResponse> getStapleBasketByZip(String stateCode,
                                                              String period,
                                                              Instant fromInclusive,
                                                              Instant toExclusive,
                                                              String source) {

        List<String> upcs = staplesProps.upcs();
        if (upcs == null || upcs.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "No staples configured: where2move.staples.upcs");
        }

        Duration d = normalizePeriod(period); // default 30d
        Range range = normalizeRange(fromInclusive, toExclusive, d);
        Short sourceSystemId = normalizeSourceSystemId(source);

        String sql = """
            WITH staple_products AS (
                SELECT id AS product_pk, upc, name, categories
                FROM fdp_grocery.source_product
                WHERE upc = ANY(:upcs)
                  AND (:sourceSystemId IS NULL OR source_system_id = :sourceSystemId)
            )
            SELECT
                sl.state_code,
                sl.postal_code,
                sp.upc,
                sp.name,
                sp.categories,
                AVG(po.price) AS avg_price,
                MIN(po.price) AS min_price,
                MAX(po.price) AS max_price,
                COUNT(*) AS sample_size
            FROM fdp_grocery.price_observation po
            JOIN fdp_grocery.store_location sl
              ON sl.id = po.store_location_id
            JOIN staple_products sp
              ON sp.product_pk = po.source_product_pk
            WHERE sl.postal_code IS NOT NULL
              AND (:stateCode IS NULL OR sl.state_code = :stateCode)
              AND po.observed_at >= :fromTs
              AND po.observed_at <  :toTs
              AND (:sourceSystemId IS NULL OR po.source_system_id = :sourceSystemId)
            GROUP BY sl.state_code, sl.postal_code, sp.upc, sp.name, sp.categories
            ORDER BY sl.postal_code ASC, sp.upc ASC
            """;

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("upcs", upcs.toArray(String[]::new))
                .addValue("stateCode", blankToNull(stateCode))
                .addValue("fromTs", Timestamp.from(range.fromInclusive()))
                .addValue("toTs", Timestamp.from(range.toExclusive()))
                .addValue("sourceSystemId", sourceSystemId);

        record Row(String state, String zip, String upc, String name,
                   List<String> categories,
                   BigDecimal avg, BigDecimal min, BigDecimal max, long n) {}

        List<Row> rows = jdbc.query(sql, p, (rs, rowNum) -> {
            Array arr = rs.getArray("categories");
            List<String> cats = (arr == null) ? List.of() : Arrays.asList((String[]) arr.getArray());

            return new Row(
                    rs.getString("state_code"),
                    rs.getString("postal_code"),
                    rs.getString("upc"),
                    rs.getString("name"),
                    cats,
                    rs.getBigDecimal("avg_price"),
                    rs.getBigDecimal("min_price"),
                    rs.getBigDecimal("max_price"),
                    rs.getLong("sample_size")
            );
        });

        if (rows.isEmpty()) {
            log.warn("Staple basket query returned 0 rows. Check where2move.staples.upcs config (no matching UPCs ingested yet). " +
                            "stateCode={}, period={}, from={}, to={}, source={}",
                    stateCode, (period == null || period.isBlank()) ? "30d" : period, range.fromInclusive(), range.toExclusive(), source);
        }

        Map<String, List<Row>> byZip = rows.stream().collect(Collectors.groupingBy(Row::zip));

        List<StapleZipBasketResponse> out = new ArrayList<>();
        for (Map.Entry<String, List<Row>> e : byZip.entrySet()) {
            String zip = e.getKey();
            List<Row> zipRows = e.getValue();

            String st = zipRows.stream().map(Row::state).filter(Objects::nonNull).findFirst().orElse(null);

            List<StapleZipBasketResponse.StapleItemAvg> items = zipRows.stream()
                    .map(r -> new StapleZipBasketResponse.StapleItemAvg(
                            r.upc,
                            r.name,
                            r.categories,
                            r.avg == null ? null : r.avg.setScale(2, RoundingMode.HALF_UP),
                            r.min,
                            r.max,
                            r.n
                    ))
                    .toList();

            BigDecimal basketAvg = items.stream()
                    .map(StapleZipBasketResponse.StapleItemAvg::avgPrice)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            long totalSamples = items.stream().mapToLong(StapleZipBasketResponse.StapleItemAvg::sampleSize).sum();

            out.add(new StapleZipBasketResponse(
                    st,
                    zip,
                    range.fromInclusive(),
                    range.toExclusive(),
                    basketAvg,
                    totalSamples,
                    items
            ));
        }

        out.sort(Comparator.comparing(StapleZipBasketResponse::zipCode));
        return out;
    }

    // -----------------------------
    // Helpers
    // -----------------------------
    private record Range(Instant fromInclusive, Instant toExclusive) {}

    private Range normalizeRange(Instant fromInclusive, Instant toExclusive, Duration periodIfMissing) {
        Instant to = (toExclusive != null) ? toExclusive : Instant.now();
        Instant from;

        if (fromInclusive != null) {
            from = fromInclusive;
        } else if (periodIfMissing != null) {
            from = to.minus(periodIfMissing);
        } else {
            throw new ResponseStatusException(BAD_REQUEST, "from is required when period is not provided");
        }

        if (!from.isBefore(to)) {
            throw new ResponseStatusException(BAD_REQUEST, "from must be before to");
        }
        return new Range(from, to);
    }

    private Duration normalizePeriod(String period) {
        if (period == null || period.isBlank()) return Duration.ofDays(30);
        return switch (period.trim().toLowerCase(Locale.ROOT)) {
            case "7d" -> Duration.ofDays(7);
            case "30d" -> Duration.ofDays(30);
            case "90d" -> Duration.ofDays(90);
            default -> throw new ResponseStatusException(BAD_REQUEST, "period must be one of: 7d, 30d, 90d");
        };
    }

    private Short normalizeSourceSystemId(String source) {
        if (source == null || source.isBlank()) return null;
        return sourceSystemService.getRequiredIdByCode(source.trim().toUpperCase(Locale.ROOT));
    }

    private String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

}