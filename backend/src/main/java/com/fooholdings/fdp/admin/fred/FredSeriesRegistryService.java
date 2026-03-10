package com.fooholdings.fdp.admin.fred;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fooholdings.fdp.geo.repo.GeoAreaJdbcRepository;
import com.fooholdings.fdp.sources.fred.ingestion.FredSeriesCatalog;
import com.fooholdings.fdp.sources.fred.ingestion.FredSeriesDefinition;

@Service
public class FredSeriesRegistryService {

    private final FredSeriesCatalog    catalog;
    private final GeoAreaJdbcRepository geoRepo;
    private final JdbcTemplate         jdbc;

    public FredSeriesRegistryService(FredSeriesCatalog catalog,
                                     GeoAreaJdbcRepository geoRepo,
                                     JdbcTemplate jdbc) {
        this.catalog = catalog;
        this.geoRepo = geoRepo;
        this.jdbc    = jdbc;
    }

    public List<FredSeriesRegistryRowDto> listSeries() {
        List<FredSeriesRegistryRowDto> out = new ArrayList<>();
        for (FredSeriesDefinition def : catalog.all()) {
            Optional<UUID> geoId = geoRepo.findGeoId(def.geoLevel(), def.geoKeyType(), def.geoKey());
            var lastDate = geoId.map(id -> jdbc.query(
                    """
                    select max(snapshot_period)
                    from fdp_geo.area_snapshot
                    where geo_id   = ?
                      and category = ?
                      and source   = 'FRED'
                    """,
                    rs -> rs.next() && rs.getDate(1) != null ? rs.getDate(1).toLocalDate() : null,
                    id, def.category()
            )).orElse(null);

            out.add(new FredSeriesRegistryRowDto(
                    def.seriesId(),
                    def.category(),
                    def.geoLevel(),
                    def.geoKey(),
                    def.enabled(),
                    lastDate
            ));
        }
        return out;
    }
}