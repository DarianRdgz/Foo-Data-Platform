package com.fooholdings.fdp.grocery.price;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JDBC write path for fdp_grocery.price_observation.
 *
 * Core dedup strategy — ON CONFLICT DO NOTHING:
 *   The schema has a UNIQUE constraint on (source_system_id, store_location_id,
 *   source_product_pk, observed_at). When re-running ingestion for the same
 *   time window the duplicate rows are skipped at the DB level
 *
 * Batch sizing:
 *   JDBC batches of 500 rows balance memory usage and round-trip cost
 */
@Repository
public class PriceObservationJdbcRepository {

    private static final Logger log = LoggerFactory.getLogger(PriceObservationJdbcRepository.class);
    private static final int BATCH_SIZE = 500;

    private final JdbcTemplate jdbc;

    public PriceObservationJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Batch-inserts price observations, silently skipping duplicates.
     *
     * @param rows list of observation rows to insert
     * @return count of rows actually inserted (duplicates are not counted)
     */
    public int batchInsert(List<PriceObservationRow> rows) {
        if (rows == null || rows.isEmpty()) return 0;

        int inserted = 0;
        List<Object[]> batchArgs = new ArrayList<>(BATCH_SIZE);

        for (int i = 0; i < rows.size(); i++) {
            PriceObservationRow r = rows.get(i);
            batchArgs.add(new Object[]{
                    r.sourceSystemId(),
                    r.storeLocationId(),
                    r.sourceProductPk(),
                    Timestamp.from(r.observedAt()),
                    r.currencyCode(),
                    r.price(),
                    r.regularPrice(),
                    r.promoPrice(),
                    r.isOnSale(),
                    r.ingestionRunId(),
                    r.rawPayloadId()
            });

            boolean lastItem = (i == rows.size() - 1);
            if (batchArgs.size() == BATCH_SIZE || lastItem) {
                int[] results = jdbc.batchUpdate("""
                        INSERT INTO fdp_grocery.price_observation
                          (source_system_id, store_location_id, source_product_pk,
                           observed_at, currency_code, price, regular_price, promo_price,
                           is_on_sale, ingestion_run_id, raw_payload_id)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (source_system_id, store_location_id, source_product_pk, observed_at)
                        DO NOTHING
                        """, batchArgs);
                for (int r2 : results) inserted += Math.max(r2, 0);
                batchArgs.clear();
            }
        }

        log.debug("[price-obs] Inserted {}/{} rows (remainder were duplicates)", inserted, rows.size());
        return inserted;
    }

    /**
     * Value record representing one row to be inserted.
     * Records are immutable and safe to build on multiple threads.
     */
    public record PriceObservationRow(
            short sourceSystemId,
            UUID storeLocationId,
            UUID sourceProductPk,
            Instant observedAt,
            String currencyCode,
            BigDecimal price,
            BigDecimal regularPrice,
            BigDecimal promoPrice,
            boolean isOnSale,
            UUID ingestionRunId,
            UUID rawPayloadId
    ) {}
}
