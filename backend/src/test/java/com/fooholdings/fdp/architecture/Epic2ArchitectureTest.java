package com.fooholdings.fdp.architecture;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class Epic2ArchitectureTest {

    @Test
    void coreMustNotDependOnKrogerPackage() {
        var classes = new ClassFileImporter()
                .importPackages("com.fooholdings.fdp");

        noClasses()
                .that().resideInAPackage("com.fooholdings.fdp.core..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.fooholdings.fdp.sources.kroger.."
                )
                .check(classes);
    }

    @Test
    void ingestionServicesMustNotDependOnKrogerClientPackage() {
        var classes = new ClassFileImporter()
                .importPackages("com.fooholdings.fdp");

        noClasses()
                .that().resideInAPackage("com.fooholdings.fdp.sources..ingestion..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.fooholdings.fdp.sources.kroger.client.."
                )
                .check(classes);
    }
}