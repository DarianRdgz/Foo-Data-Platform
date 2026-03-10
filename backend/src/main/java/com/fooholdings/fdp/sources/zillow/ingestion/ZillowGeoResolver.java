package com.fooholdings.fdp.sources.zillow.ingestion;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.fooholdings.fdp.geo.repo.GeoAreaJdbcRepository;
import com.fooholdings.fdp.sources.zillow.csv.ZillowCsvRecord;
import org.springframework.stereotype.Component;

@Component
public class ZillowGeoResolver {

    private final GeoAreaJdbcRepository geoRepo;

    public ZillowGeoResolver(GeoAreaJdbcRepository geoRepo) {
        this.geoRepo = geoRepo;
    }

    public Optional<UUID> resolve(ZillowCsvRecord row) {
        String type = normalize(row.regionType());

        return switch (type) {
            case "country", "national" -> geoRepo.findNationalGeoId();
            case "state" -> resolveState(row);
            case "county" -> resolveCounty(row);
            case "city" -> resolveCity(row);
            case "zip", "zipcode" -> resolveZip(row);
            case "msa", "metro" -> resolveMetro(row);
            default -> Optional.empty();
        };
    }

    private Optional<UUID> resolveState(ZillowCsvRecord row) {
        String fips = StateFipsLookup.fromCode(row.regionName(), row.stateCode(), row.stateName());
        if (fips == null) {
            return Optional.empty();
        }
        return geoRepo.findStateGeoIdByFips(fips);
    }

    private Optional<UUID> resolveCounty(ZillowCsvRecord row) {
        String stateFips = StateFipsLookup.fromCode(row.stateCode(), row.stateCode(), row.stateName());
        if (stateFips == null || !hasText(row.regionName())) {
            return Optional.empty();
        }
        return geoRepo.findCountyGeoIdByNameAndStateFips(stripCountySuffix(row.regionName()), stateFips)
                .or(() -> geoRepo.findCountyGeoIdByNameAndStateFips(stripCountySuffix(row.countyName()), stateFips));
    }

    private Optional<UUID> resolveCity(ZillowCsvRecord row) {
        String stateFips = StateFipsLookup.fromCode(row.stateCode(), row.stateCode(), row.stateName());
        if (stateFips == null || !hasText(row.regionName())) {
            return Optional.empty();
        }
        return geoRepo.findCityGeoIdByNameAndStateFips(row.regionName(), stateFips);
    }

    private Optional<UUID> resolveZip(ZillowCsvRecord row) {
        if (!hasText(row.regionName())) {
            return Optional.empty();
        }
        String zip = row.regionName().trim();
        return geoRepo.findZipGeoIdByZipCode(zip);
    }

    private Optional<UUID> resolveMetro(ZillowCsvRecord row) {
        if (hasText(row.regionId())) {
            Optional<UUID> byCbsa = geoRepo.findMetroGeoIdByCbsaCode(row.regionId());
            if (byCbsa.isPresent()) {
                return byCbsa;
            }
        }

        if (hasText(row.regionName())) {
            return geoRepo.findMetroGeoIdByName(normalizeMetroName(row.regionName()));
        }

        return Optional.empty();
    }

    private static String stripCountySuffix(String raw) {
        if (!hasText(raw)) {
            return raw;
        }
        String value = raw.trim();
        value = value.replaceAll("\\s+County$", "");
        value = value.replaceAll("\\s+Parish$", "");
        value = value.replaceAll("\\s+Borough$", "");
        value = value.replaceAll("\\s+Census Area$", "");
        return value;
    }

    private static String normalizeMetroName(String raw) {
        if (!hasText(raw)) {
            return raw;
        }
        return raw.trim();
    }

    private static boolean hasText(String raw) {
        return raw != null && !raw.trim().isEmpty();
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }
}