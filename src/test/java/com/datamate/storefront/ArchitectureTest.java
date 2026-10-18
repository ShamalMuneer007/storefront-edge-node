package com.datamate.storefront;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.datamate.storefront", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String[] FRAMEWORKS = {
        "org.springframework..", "jakarta..", "java.sql..", "javax.sql..", "tools.jackson..", "com.fasterxml.."
    };

    @ArchTest
    static final ArchRule domainIsPureJava = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORKS)
            .orShould().dependOnClassesThat().resideInAnyPackage("..application..", "..infrastructure..");

    @ArchTest
    static final ArchRule applicationIsFrameworkFree = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORKS)
            .orShould().dependOnClassesThat().resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule restCallsUseCasesOnly = noClasses()
            .that().resideInAPackage("..adapter.inbound.rest..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..application.service..", "..application.port.outbound..", "..adapter.outbound..");

    @ArchTest
    static final ArchRule outboundAdaptersDoNotCallInbound = noClasses()
            .that().resideInAPackage("..adapter.outbound..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.inbound..");

    @ArchTest
    static final ArchRule servicesAreWiredOnlyByConfig = noClasses()
            .that().resideOutsideOfPackages("..application.service..", "..infrastructure.config..")
            .should().dependOnClassesThat().resideInAPackage("..application.service..");
}
