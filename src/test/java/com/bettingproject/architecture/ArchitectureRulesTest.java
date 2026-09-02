package com.bettingproject.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(packages = "com.bettingproject", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule business_modules_are_free_of_cycles = slices()
            .matching("com.bettingproject.(*)..")
            .should()
            .beFreeOfCycles();

    @ArchTest
    static final ArchRule domain_is_independent_from_frameworks_and_adapters = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "..adapter..");

    @ArchTest
    static final ArchRule application_does_not_depend_on_adapters = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..adapter..");

    @ArchTest
    static final ArchRule application_does_not_depend_on_jdbc = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "java.sql..",
                    "javax.sql..",
                    "org.springframework.jdbc..",
                    "org.postgresql..");

    @ArchTest
    static final ArchRule application_does_not_depend_on_web_or_servlet = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework.http..",
                    "org.springframework.web..",
                    "jakarta.servlet..",
                    "javax.servlet..");
}
