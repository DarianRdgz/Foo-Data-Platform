package com.fooholdings.fdp.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class Sprint5ArchitectureTest {

    @Test
    void zillowAdaptersMustNotDependOnKrogerPackages() {
        var classes = new ClassFileImporter().importPackages("com.fooholdings.fdp");

        noClasses()
                .that()
                .resideInAnyPackage("com.fooholdings.fdp.sources.zillow..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.fooholdings.fdp.sources.kroger..")
                .check(classes);
    }

    @Test
    void charterNamedZillowAdaptersExist() throws Exception {
        Class.forName("com.fooholdings.fdp.sources.zillow.ingestion.ZillowZhviAdapter");
        Class.forName("com.fooholdings.fdp.sources.zillow.ingestion.ZillowZoriAdapter");
        Class.forName("com.fooholdings.fdp.sources.zillow.ingestion.ZillowListingsAdapter");
        Class.forName("com.fooholdings.fdp.sources.zillow.ingestion.ZillowAffordabilityAdapter");
    }
}