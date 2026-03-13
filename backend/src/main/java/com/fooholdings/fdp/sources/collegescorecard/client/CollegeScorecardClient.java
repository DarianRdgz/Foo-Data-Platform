package com.fooholdings.fdp.sources.collegescorecard.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fooholdings.fdp.sources.collegescorecard.config.CollegeScorecardProperties;

/**
 * Paginates through the College Scorecard /v1/schools.json endpoint and
 * returns all school records as flat maps.
 *
 * Fields requested:
 *   school.name, school.state, school.city,
 *   latest.admissions.admission_rate.overall,
 *   latest.cost.avg_net_price.public, latest.cost.avg_net_price.private,
 *   latest.earnings.10_yrs_after_entry.median,
 *   latest.aid.pell_grant_rate
 */
@Component
public class CollegeScorecardClient {

    private static final Logger log = LoggerFactory.getLogger(CollegeScorecardClient.class);

    static final String FIELDS =
            "school.name,school.state,school.city," +
            "latest.admissions.admission_rate.overall," +
            "latest.cost.avg_net_price.public,latest.cost.avg_net_price.private," +
            "latest.earnings.10_yrs_after_entry.median," +
            "latest.aid.pell_grant_rate";

    private final RestClient restClient;
    private final CollegeScorecardProperties props;

    public CollegeScorecardClient(RestClient.Builder builder, CollegeScorecardProperties props) {
        this.props = props;
        this.restClient = builder.baseUrl(props.baseUrl()).build();
    }

    /**
     * Fetches all schools, paginating until exhausted.
     *
     * @return flat list of school attribute maps; keys use dot-notation exactly
     *         as returned by the API (e.g. "school.state", "latest.aid.pell_grant_rate")
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchAllSchools() {
        List<Map<String, Object>> all = new ArrayList<>();
        int page = 0;
        int total;

        do {
            final int currentPage = page;
            Map<String, Object> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(props.schoolsPath())
                            .queryParam("api_key", props.apiKey())
                            .queryParam("fields", FIELDS)
                            .queryParam("per_page", props.pageSize())
                            .queryParam("page", currentPage)
                            .build())
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                log.warn("CollegeScorecard: null response on page {}", currentPage);
                break;
            }

            Map<String, Object> metadata = (Map<String, Object>) response.get("metadata");
            total = metadata != null ? ((Number) metadata.getOrDefault("total", 0)).intValue() : 0;

            List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
            if (results == null || results.isEmpty()) break;

            all.addAll(results);
            log.debug("CollegeScorecard: page={} fetched={} total={}", currentPage, results.size(), total);
            page++;

        } while (all.size() < total);

        log.info("CollegeScorecard: fetched {} school records across {} pages", all.size(), page);
        return all;
    }
}