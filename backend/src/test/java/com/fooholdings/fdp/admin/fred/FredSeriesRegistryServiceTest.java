package com.fooholdings.fdp.admin.fred;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import com.fooholdings.fdp.geo.repo.GeoAreaJdbcRepository;
import com.fooholdings.fdp.sources.fred.ingestion.FredSeriesCatalog;
import com.fooholdings.fdp.sources.fred.ingestion.FredSeriesDefinition;

class FredSeriesRegistryServiceTest {

    @SuppressWarnings("unchecked")
    @Test
    void returnsConfiguredSeriesWithLastObservationDate() {
        FredSeriesCatalog catalog = mock(FredSeriesCatalog.class);
        GeoAreaJdbcRepository geoRepo = mock(GeoAreaJdbcRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        FredSeriesDefinition def = new FredSeriesDefinition(
                "UNRATE", "economic.unemployment_rate", "national", "none", "US", true);

        UUID geoId = UUID.randomUUID();
        when(catalog.all()).thenReturn(List.of(def));
        when(geoRepo.findGeoId("national", "none", "US")).thenReturn(Optional.of(geoId));
        when(jdbc.query(anyString(), any(ResultSetExtractor.class),
                        eq(geoId), eq("economic.unemployment_rate")))
                .thenReturn(LocalDate.of(2026, 1, 31));

        FredSeriesRegistryService service = new FredSeriesRegistryService(catalog, geoRepo, jdbc);
        List<FredSeriesRegistryRowDto> rows = service.listSeries();

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().seriesId()).isEqualTo("UNRATE");
        assertThat(rows.getFirst().lastObservationDate()).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(rows.getFirst().enabled()).isTrue();
    }

    @Test
    void seriesWithNoGeoMatchReturnsNullLastObservationDate() {
        FredSeriesCatalog catalog = mock(FredSeriesCatalog.class);
        GeoAreaJdbcRepository geoRepo = mock(GeoAreaJdbcRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        FredSeriesDefinition def = new FredSeriesDefinition(
                "UNMAPPED", "economic.something", "county", "fips", "99999", true);

        when(catalog.all()).thenReturn(List.of(def));
        when(geoRepo.findGeoId("county", "fips", "99999")).thenReturn(Optional.empty());

        FredSeriesRegistryService service = new FredSeriesRegistryService(catalog, geoRepo, jdbc);
        List<FredSeriesRegistryRowDto> rows = service.listSeries();

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().lastObservationDate()).isNull();
        verifyNoInteractions(jdbc);
    }
}