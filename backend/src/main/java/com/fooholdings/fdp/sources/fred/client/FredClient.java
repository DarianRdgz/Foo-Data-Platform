package com.fooholdings.fdp.sources.fred.client;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fooholdings.fdp.sources.fred.config.FredProperties;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;

/**
 * Thin HTTP client for the FRED REST API.
 *
 * Rate limiting: FRED's standard API key allows 120 requests/minute.
 * We configure a RateLimiter at 100/min to leave headroom.
 *
 * Incremental fetch: callers pass an observationStart date so we only
 * retrieve new observations on subsequent runs, not full history.
 */
@Component
public class FredClient {

    private static final Logger log = LoggerFactory.getLogger(FredClient.class);

    private final RestClient restClient;
    private final FredProperties props;
    private final RateLimiter rateLimiter;

    public FredClient(RestClient.Builder builder, FredProperties props) {
        this.props = props;
        this.restClient = builder.baseUrl(props.baseUrl()).build();

        int limitPerMin = props.rateLimiter() != null ? props.rateLimiter().limitForPeriodPerMinute() : 100;
        long timeoutMs  = props.rateLimiter() != null ? props.rateLimiter().timeoutMillis() : 5000L;

        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(limitPerMin)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ofMillis(timeoutMs))
                .build();
        this.rateLimiter = RateLimiterRegistry.of(config).rateLimiter("fred");
    }

    /**
     * Fetches metadata for a single FRED series.
     */
    public FredSeriesMeta getSeries(String seriesId) {
        return RateLimiter.decorateSupplier(rateLimiter, () -> {
            log.debug("FRED: GET series metadata for {}", seriesId);
            FredSeriesResponse resp = restClient.get()
                    .uri("/fred/series?series_id={id}&api_key={key}&file_type=json",
                         seriesId, props.apiKey())
                    .retrieve()
                    .body(FredSeriesResponse.class);
            if (resp == null || resp.seriess() == null || resp.seriess().isEmpty()) {
                throw new IllegalStateException("No FRED series metadata for: " + seriesId);
            }
            return resp.seriess().getFirst();
        }).get();
    }

    /**
     * Fetches observations for a series, starting from observationStart (inclusive).
     * Pass LocalDate.of(1776,1,1) for a full history load on first ingest.
     * "." values (FRED's marker for missing/suppressed data) are filtered out.
     */
    public List<FredObservation> getObservations(String seriesId, LocalDate observationStart) {
        return RateLimiter.decorateSupplier(rateLimiter, () -> {
            log.debug("FRED: GET observations for {} since {}", seriesId, observationStart);
            FredObservationsResponse resp = restClient.get()
                    .uri("/fred/series/observations?series_id={id}&api_key={key}" +
                         "&file_type=json&observation_start={start}&vintage_dates=",
                         seriesId, props.apiKey(), observationStart)
                    .retrieve()
                    .body(FredObservationsResponse.class);
            if (resp == null || resp.observations() == null) {
                return List.<FredObservation>of();
            }
            List<FredObservation> valid = new ArrayList<>();
            for (FredObservation obs : resp.observations()) {
                if (obs.value() != null && !obs.value().equals(".")) {
                    valid.add(obs);
                }
            }
            return valid;
        }).get();
    }

    // ── Response DTOs (record-mapped from FRED JSON) ──────────────────────

    public record FredSeriesResponse(List<FredSeriesMeta> seriess) {}

    public record FredSeriesMeta(
            String id,
            String title,
            String units,
            String frequency,
            String seasonal_adjustment,
            String last_updated
    ) {}

    public record FredObservationsResponse(
            String observation_start,
            String observation_end,
            List<FredObservation> observations
    ) {}

    public record FredObservation(
            String date,
            String value,
            String realtime_start,
            String realtime_end
    ) {}
}