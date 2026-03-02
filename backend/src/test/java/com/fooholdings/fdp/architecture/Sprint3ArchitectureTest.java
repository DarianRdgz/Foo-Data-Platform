package com.fooholdings.fdp.architecture;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Sprint 3 charter rule: controllers inject services only, never repositories.
 */
public class Sprint3ArchitectureTest {

    @Test
    void apiControllersMustNotDependOnRepositories() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.fooholdings.fdp");

        noClasses()
                .that().resideInAPackage("..api.web..")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                .check(classes);
    }
}