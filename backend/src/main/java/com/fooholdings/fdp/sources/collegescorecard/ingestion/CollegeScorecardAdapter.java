package com.fooholdings.fdp.sources.collegescorecard.ingestion;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fooholdings.fdp.geo.repo.AreaSnapshotJdbcRepository;
import com.fooholdings.fdp.geo.repo.AreaSnapshotJdbcRepository.AreaSnapshotUpsert;
import com.fooholdings.fdp.geo.repo.GeoAreaJdbcRepository;
import com.fooholdings.fdp.geo.support.StateAbbreviationLookup;
import com.fooholdings.fdp.sources.collegescorecard.client.CollegeScorecardClient;

/**
 * Aggregates College Scorecard school records into area_snapshot rows.
 *
 * Geo levels written: state and city only.
 *   - No county rows: the API provides no county identifier.
 *   - City rows are written only when geo_areas contains a matching city record
 *     (seeded from the College Scorecard city+state crosswalk per the charter).
 *
 * Categories written (one row per category per geo per snapshot period):
 *   education.postsecondary.school_count
 *   education.postsecondary.avg_admission_rate
 *   education.postsecondary.avg_net_price
 *   education.postsecondary.avg_median_earnings_10yr
 *   education.postsecondary.pct_with_financial_aid
 *
 * Snapshot period: Dec 31 of the current calendar year, matching the CDE annual convention.
 */
@Component
public class CollegeScorecardAdapter {

    private static final Logger log = LoggerFactory.getLogger(CollegeScorecardAdapter.class);

    static final String SOURCE = "COLLEGE_SCORECARD";

    static final String CAT_SCHOOL_COUNT    = "education.postsecondary.school_count";
    static final String CAT_ADMISSION_RATE  = "education.postsecondary.avg_admission_rate";
    static final String CAT_NET_PRICE       = "education.postsecondary.avg_net_price";
    static final String CAT_EARNINGS_10YR   = "education.postsecondary.avg_median_earnings_10yr";
    static final String CAT_FINANCIAL_AID   = "education.postsecondary.pct_with_financial_aid";

    private final CollegeScorecardClient client;
    private final GeoAreaJdbcRepository geoRepo;
    private final AreaSnapshotJdbcRepository snapshotRepo;

    public CollegeScorecardAdapter(CollegeScorecardClient client,
                                   GeoAreaJdbcRepository geoRepo,
                                   AreaSnapshotJdbcRepository snapshotRepo) {
        this.client = client;
        this.geoRepo = geoRepo;
        this.snapshotRepo = snapshotRepo;
    }

    public int ingest() {
        List<Map<String, Object>> schools = client.fetchAllSchools();
        log.info("CollegeScorecard: aggregating {} school records", schools.size());

        LocalDate snapshotPeriod = LocalDate.of(LocalDate.now().getYear(), 12, 31);

        List<AreaSnapshotUpsert> rows = new ArrayList<>();
        rows.addAll(buildStateRows(schools, snapshotPeriod));
        rows.addAll(buildCityRows(schools, snapshotPeriod));

        int written = snapshotRepo.batchUpsert(deduplicate(rows));
        log.info("CollegeScorecard: wrote {} area_snapshot rows (period={})", written, snapshotPeriod);
        return written;
    }

    // ── State aggregation ─────────────────────────────────────────────────

    private List<AreaSnapshotUpsert> buildStateRows(List<Map<String, Object>> schools,
                                                     LocalDate period) {
        // Key: stateFips → aggregator
        Map<String, SchoolAggregator> byState = new HashMap<>();

        for (Map<String, Object> school : schools) {
            String abbr = stringVal(school, "school.state");
            if (abbr == null) continue;

            String fips = StateAbbreviationLookup.fipsFromAbbreviation(abbr);
            if (fips == null) {
                log.warn("CollegeScorecard: unknown state abbreviation '{}' — skipping school", abbr);
                continue;
            }
            byState.computeIfAbsent(fips, k -> new SchoolAggregator()).add(school);
        }

        List<AreaSnapshotUpsert> rows = new ArrayList<>();
        int misses = 0;

        for (Map.Entry<String, SchoolAggregator> entry : byState.entrySet()) {
            Optional<UUID> geoId = geoRepo.findStateGeoIdByFips(entry.getKey());
            if (geoId.isEmpty()) {
                log.warn("CollegeScorecard: no geo_id for state fips='{}' — skipping", entry.getKey());
                misses++;
                continue;
            }
            rows.addAll(buildRows(geoId.get(), "state", entry.getValue(), period));
        }

        log.info("CollegeScorecard: state aggregation — {} states written, {} missed",
                 byState.size() - misses, misses);
        return rows;
    }

    // ── City aggregation ─────────────────────────────────────────────────

    private List<AreaSnapshotUpsert> buildCityRows(List<Map<String, Object>> schools,
                                                    LocalDate period) {
        // Key: "cityName|stateFips"
        Map<String, SchoolAggregator> byCity = new HashMap<>();
        Map<String, String[]> cityKey = new HashMap<>(); // key → [cityName, stateFips]

        for (Map<String, Object> school : schools) {
            String abbr = stringVal(school, "school.state");
            String city = stringVal(school, "school.city");
            if (abbr == null || city == null) continue;

            String fips = StateAbbreviationLookup.fipsFromAbbreviation(abbr);
            if (fips == null) continue;

            String key = city.toLowerCase() + "|" + fips;
            byCity.computeIfAbsent(key, k -> new SchoolAggregator()).add(school);
            cityKey.putIfAbsent(key, new String[]{city, fips});
        }

        List<AreaSnapshotUpsert> rows = new ArrayList<>();
        int misses = 0;

        for (Map.Entry<String, SchoolAggregator> entry : byCity.entrySet()) {
            String[] parts = cityKey.get(entry.getKey());
            Optional<UUID> geoId = geoRepo.findCityGeoIdByNameAndStateFips(parts[0], parts[1]);
            if (geoId.isEmpty()) {
                // City miss is expected — not all College Scorecard cities are in geo_areas
                log.debug("CollegeScorecard: no geo_id for city='{}' stateFips='{}' — skipping",
                          parts[0], parts[1]);
                misses++;
                continue;
            }
            rows.addAll(buildRows(geoId.get(), "city", entry.getValue(), period));
        }

        log.info("CollegeScorecard: city aggregation — {} cities written, {} unmatched",
                 byCity.size() - misses, misses);
        return rows;
    }

    // ── Row builder ───────────────────────────────────────────────────────

    private List<AreaSnapshotUpsert> buildRows(UUID geoId, String geoLevel,
                                               SchoolAggregator agg, LocalDate period) {
        List<AreaSnapshotUpsert> rows = new ArrayList<>();

        rows.add(new AreaSnapshotUpsert(geoId, CAT_SCHOOL_COUNT, period, SOURCE,
                payload("schoolCount", agg.count, "geoLevel", geoLevel)));

        if (agg.admissionRateSum > 0 && agg.admissionRateCount > 0) {
            rows.add(new AreaSnapshotUpsert(geoId, CAT_ADMISSION_RATE, period, SOURCE,
                    payload("avgAdmissionRate", agg.admissionRateSum / agg.admissionRateCount,
                            "schoolsWithData", agg.admissionRateCount, "geoLevel", geoLevel)));
        }
        if (agg.netPriceSum > 0 && agg.netPriceCount > 0) {
            rows.add(new AreaSnapshotUpsert(geoId, CAT_NET_PRICE, period, SOURCE,
                    payload("avgNetPriceUsd", agg.netPriceSum / agg.netPriceCount,
                            "schoolsWithData", agg.netPriceCount, "geoLevel", geoLevel)));
        }
        if (agg.earningsSum > 0 && agg.earningsCount > 0) {
            rows.add(new AreaSnapshotUpsert(geoId, CAT_EARNINGS_10YR, period, SOURCE,
                    payload("avgMedianEarnings10yrUsd", agg.earningsSum / agg.earningsCount,
                            "schoolsWithData", agg.earningsCount, "geoLevel", geoLevel)));
        }
        if (agg.financialAidSum > 0 && agg.financialAidCount > 0) {
            rows.add(new AreaSnapshotUpsert(geoId, CAT_FINANCIAL_AID, period, SOURCE,
                    payload("pctWithFinancialAid", agg.financialAidSum / agg.financialAidCount,
                            "schoolsWithData", agg.financialAidCount, "geoLevel", geoLevel)));
        }

        return rows;
    }

    // ── Aggregator ────────────────────────────────────────────────────────

    private static class SchoolAggregator {
        int count;
        double admissionRateSum; int admissionRateCount;
        double netPriceSum;      int netPriceCount;
        double earningsSum;      int earningsCount;
        double financialAidSum;  int financialAidCount;

        void add(Map<String, Object> school) {
            count++;
            Double admRate = doubleVal(school, "latest.admissions.admission_rate.overall");
            if (admRate != null) { admissionRateSum += admRate; admissionRateCount++; }

            Double netPrice = netPrice(school);
            if (netPrice != null) { netPriceSum += netPrice; netPriceCount++; }

            Double earnings = doubleVal(school, "latest.earnings.10_yrs_after_entry.median");
            if (earnings != null) { earningsSum += earnings; earningsCount++; }

            Double pellRate = doubleVal(school, "latest.aid.pell_grant_rate");
            if (pellRate != null) { financialAidSum += pellRate; financialAidCount++; }
        }

        private static Double netPrice(Map<String, Object> school) {
            Double pub = doubleVal(school, "latest.cost.avg_net_price.public");
            if (pub != null) return pub;
            return doubleVal(school, "latest.cost.avg_net_price.private");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static String stringVal(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static Double doubleVal(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        try { return ((Number) v).doubleValue(); }
        catch (ClassCastException e) { return null; }
    }

    private static Map<String, Object> payload(Object... keyValues) {
        Map<String, Object> p = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length - 1; i += 2) {
            p.put(keyValues[i].toString(), keyValues[i + 1]);
        }
        p.put("frequency", "annual");
        return p;
    }

    private static List<AreaSnapshotUpsert> deduplicate(List<AreaSnapshotUpsert> rows) {
        Map<String, AreaSnapshotUpsert> seen = new LinkedHashMap<>();
        for (AreaSnapshotUpsert r : rows) {
            seen.put(r.geoId() + "|" + r.category() + "|" + r.snapshotPeriod() + "|" + r.source(), r);
        }
        return new ArrayList<>(seen.values());
    }
}