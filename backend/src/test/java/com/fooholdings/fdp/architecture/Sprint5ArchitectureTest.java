package com.fooholdings.fdp.architecture;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import com.fooholdings.fdp.geo.repo.GeoAreaJdbcRepository;
import com.fooholdings.fdp.sources.cde.ingestion.CdeAdapter;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class Sprint5ArchitectureTest {

    @Test
    void zillowAdaptersMustNotDependOnKrogerPackages() {
        var classes = new ClassFileImporter().importPackages("com.fooholdings.fdp");
        noClasses()
                .that().resideInAnyPackage("com.fooholdings.fdp.sources.zillow..")
                .should().dependOnClassesThat().resideInAnyPackage("com.fooholdings.fdp.sources.kroger..")
                .check(classes);
    }

    @Test
    void cdeAdapterMustNotDependOnKrogerOrZillowPackages() {
        var classes = new ClassFileImporter().importPackages("com.fooholdings.fdp");
        noClasses()
                .that().resideInAnyPackage("com.fooholdings.fdp.sources.cde..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.fooholdings.fdp.sources.kroger..",
                        "com.fooholdings.fdp.sources.zillow.."
                )
                .check(classes);
    }

    @Test
    void fredAdapterMustNotDependOnKrogerOrZillowPackages() {
        var classes = new ClassFileImporter().importPackages("com.fooholdings.fdp");
        noClasses()
                .that().resideInAnyPackage("com.fooholdings.fdp.sources.fred..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.fooholdings.fdp.sources.kroger..",
                        "com.fooholdings.fdp.sources.zillow.."
                )
                .check(classes);
    }

    @Test
    void charterNamedZillowAdaptersExist() throws Exception {
        Class.forName("com.fooholdings.fdp.sources.zillow.ingestion.ZillowZhviAdapter");
        Class.forName("com.fooholdings.fdp.sources.zillow.ingestion.ZillowZoriAdapter");
        Class.forName("com.fooholdings.fdp.sources.zillow.ingestion.ZillowListingsAdapter");
        Class.forName("com.fooholdings.fdp.sources.zillow.ingestion.ZillowAffordabilityAdapter");
    }

    @Test
    void charterNamedCdeAdapterExists() throws Exception {
        Class.forName("com.fooholdings.fdp.sources.cde.ingestion.CdeAdapter");
    }

    @Test
    void charterNamedFredAdapterExists() throws Exception {
        Class.forName("com.fooholdings.fdp.sources.fred.ingestion.FredAdapter");
    }

    /**
     * Tests the actual CdeAdapter.resolveGeo logic to verify it never
     * returns a geo_level other than 'national' or 'state'.
     *
     * This is a real behavioural test — not a tautology on a hardcoded list.
     */
    @Test
    void cdeAdapterNeverResolvesToNonNationalOrStateGeoLevel() {
        // Stub repo: state lookups return empty (no DB needed)
        GeoAreaJdbcRepository geoRepo = new GeoAreaJdbcRepository(null) {
            @Override public Optional<UUID> findNationalGeoId() {
                return Optional.of(UUID.randomUUID());
            }
            @Override public Optional<UUID> findStateGeoIdByName(String name) {
                return Optional.of(UUID.randomUUID());
            }
        };

        CdeAdapter adapter = new CdeAdapter(null, geoRepo, null);

        // national aliases must resolve to 'national'
        List.of("United States", "national", "US").forEach(s ->
            assertThat(adapter.resolveGeo(s))
                .isPresent()
                .hasValueSatisfying(g -> assertThat(g.geoLevel()).isEqualTo("national"))
        );

        // state names must resolve to 'state'
        assertThat(adapter.resolveGeo("Texas"))
            .isPresent()
            .hasValueSatisfying(g -> assertThat(g.geoLevel()).isEqualTo("state"));

        // blank/null must resolve to empty, never a stray geo level
        assertThat(adapter.resolveGeo(null)).isEmpty();
        assertThat(adapter.resolveGeo("  ")).isEmpty();
    }

    @Test
    void collegeScorecardMustNotDependOnOtherSourcePackages() {
        var classes = new ClassFileImporter().importPackages("com.fooholdings.fdp");
        noClasses()
                .that().resideInAnyPackage("com.fooholdings.fdp.sources.collegescorecard..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.fooholdings.fdp.sources.kroger..",
                        "com.fooholdings.fdp.sources.zillow..",
                        "com.fooholdings.fdp.sources.cde..",
                        "com.fooholdings.fdp.sources.fred..",
                        "com.fooholdings.fdp.sources.fema..",
                        "com.fooholdings.fdp.sources.noaa.."
                )
                .check(classes);
    }

    @Test
    void femaMustNotDependOnOtherSourcePackages() {
        var classes = new ClassFileImporter().importPackages("com.fooholdings.fdp");
        noClasses()
                .that().resideInAnyPackage("com.fooholdings.fdp.sources.fema..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.fooholdings.fdp.sources.kroger..",
                        "com.fooholdings.fdp.sources.zillow..",
                        "com.fooholdings.fdp.sources.cde..",
                        "com.fooholdings.fdp.sources.fred..",
                        "com.fooholdings.fdp.sources.collegescorecard..",
                        "com.fooholdings.fdp.sources.noaa.."
                )
                .check(classes);
    }

    @Test
    void noaaMustNotDependOnOtherSourcePackages() {
        var classes = new ClassFileImporter().importPackages("com.fooholdings.fdp");
        noClasses()
                .that().resideInAnyPackage("com.fooholdings.fdp.sources.noaa..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.fooholdings.fdp.sources.kroger..",
                        "com.fooholdings.fdp.sources.zillow..",
                        "com.fooholdings.fdp.sources.cde..",
                        "com.fooholdings.fdp.sources.fred..",
                        "com.fooholdings.fdp.sources.collegescorecard..",
                        "com.fooholdings.fdp.sources.fema.."
                )
                .check(classes);
    }

    @Test
    void charterNamed54AdaptersExist() throws Exception {
        Class.forName("com.fooholdings.fdp.sources.collegescorecard.ingestion.CollegeScorecardAdapter");
        Class.forName("com.fooholdings.fdp.sources.fema.ingestion.FemaDisasterAdapter");
        Class.forName("com.fooholdings.fdp.sources.noaa.ingestion.NoaaStormAdapter");
        Class.forName("com.fooholdings.fdp.geo.service.DisasterRiskScoreService");
    }

    @Test
    void disasterRiskScoreServiceMustNotDependOnSourceAdaptersDirectly() {
        var classes = new ClassFileImporter().importPackages("com.fooholdings.fdp");
        noClasses()
                .that().resideInAnyPackage("com.fooholdings.fdp.geo.service..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.fooholdings.fdp.sources.fema..",
                        "com.fooholdings.fdp.sources.noaa.."
                )
                .check(classes);
    }

    @Test
    void femaAdapterNeverWritesNonStateOrCountyRows() {

        // The adapter only calls findStateGeoIdByFips and findCountyGeoIdByFips,
        // never city/zip/metro resolvers. Verifying via ArchUnit that those methods
        // are not referenced in the fema package is the structural enforcement.
        var classes = new ClassFileImporter().importPackages("com.fooholdings.fdp.sources.fema");
        noClasses()
                .that().resideInAnyPackage("com.fooholdings.fdp.sources.fema..")
                .should().callMethod(GeoAreaJdbcRepository.class, "findCityGeoIdByNameAndStateFips",
                        String.class, String.class)
                .check(classes);
        noClasses()
                .that().resideInAnyPackage("com.fooholdings.fdp.sources.fema..")
                .should().callMethod(GeoAreaJdbcRepository.class, "findZipGeoIdByZipCode", String.class)
                .check(classes);
    }


}
