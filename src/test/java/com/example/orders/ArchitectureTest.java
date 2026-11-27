package com.example.orders;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the architectural boundaries described in the design documents.
 *
 * <p>The domain layer is framework-free and may only see the shared kernel. Each bounded
 * context's domain is isolated from the other contexts, the shared kernel stays free of
 * business contexts and the event-sourcing example is deliberately standalone.
 */
@AnalyzeClasses(packages = "com.example.orders", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainHasNoFrameworkDeps =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..",
                            "java.sql..",
                            "jakarta.persistence..",
                            "com.fasterxml..",
                            "tools.jackson..");

    @ArchTest
    static final ArchRule domainDoesNotDependOnOuterLayers =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..application..",
                            "..infrastructure..",
                            "..api..");

    @ArchTest
    static final ArchRule salesDomainIsIsolated =
            noClasses().that().resideInAPackage("..sales.domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..inventory..",
                            "..billing..",
                            "..fulfillment..",
                            "..projections..");

    @ArchTest
    static final ArchRule inventoryDomainIsIsolated =
            noClasses().that().resideInAPackage("..inventory.domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..sales..",
                            "..billing..",
                            "..fulfillment..",
                            "..projections..");

    @ArchTest
    static final ArchRule billingDomainIsIsolated =
            noClasses().that().resideInAPackage("..billing.domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..sales..",
                            "..inventory..",
                            "..fulfillment..",
                            "..projections..");

    @ArchTest
    static final ArchRule fulfillmentDomainIsIsolated =
            noClasses().that().resideInAPackage("..fulfillment.domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..sales..",
                            "..inventory..",
                            "..billing..",
                            "..projections..");

    @ArchTest
    static final ArchRule sharedKernelDoesNotDependOnContexts =
            noClasses().that().resideInAPackage("..sharedkernel..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..sales..",
                            "..inventory..",
                            "..billing..",
                            "..fulfillment..",
                            "..projections..",
                            "..examples..");

    @ArchTest
    static final ArchRule eventSourcingExampleIsStandalone =
            noClasses().that().resideInAPackage("..examples.eventsourcing..")
                    .should().dependOnClassesThat().resideInAnyPackage("org.springframework..");
}
