package com.fooholdings.fdp.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.importer.ClassFileImporter;

class Epic2ArchitectureTest {

    @Test
    void coreMustNotDependOnKrogerPackage() {
        var classes = new ClassFileImporter()
                .importPackages("com.fooholdings.fdp");

        noClasses()
                .that().resideInAPackage("com.fooholdings.fdp.fdp_core..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.fooholdings.fdp.sources.kroger.."
                )
                .check(classes);
    }
}