package com.fooholdings.fdp.sources.fred.ingestion;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * Loads the committed FRED series catalog from fred-series-catalog.yml on the classpath.
 *
 * The catalog is separate from application.yml to allow it to grow to thousands of
 * entries (all state, county, and metro series) without polluting main config.
 */
@Component
public class FredSeriesCatalog {

    private final List<FredSeriesDefinition> all;

    @SuppressWarnings("unchecked")
    public FredSeriesCatalog() {
        try {
            Yaml yaml = new Yaml();
            try (var stream = new ClassPathResource("fred-series-catalog.yml").getInputStream()) {
                Map<String, Object> root = yaml.load(stream);
                Map<String, Object> fred = (Map<String, Object>) root.get("fred");
                List<Map<String, Object>> raw = (List<Map<String, Object>>) fred.get("series");

                List<FredSeriesDefinition> loaded = new ArrayList<>();
                for (Map<String, Object> entry : raw) {
                    loaded.add(new FredSeriesDefinition(
                            (String)  entry.get("seriesId"),
                            (String)  entry.get("category"),
                            (String)  entry.get("geoLevel"),
                            (String)  entry.get("geoKeyType"),
                            (String)  entry.get("geoKey"),
                            Boolean.TRUE.equals(entry.getOrDefault("enabled", true))
                    ));
                }
                this.all = List.copyOf(loaded);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load fred-series-catalog.yml from classpath", e);
        }
    }

    public List<FredSeriesDefinition> all() { return all; }

    public List<FredSeriesDefinition> allEnabled() {
        return all.stream().filter(FredSeriesDefinition::enabled).toList();
    }
}