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
 * context's domain is isolated from every other context, the shared kernel stays free of
 * business contexts and the event-sourcing example is deliberately standalone.
 *
 * <p>{@code bootstrap} is the composition root: it may reference every context's domain to
 * assemble beans. All other code must go through application ports or integration events.
 */
@AnalyzeClasses(packages = "com.example.orders", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	@ArchTest
	static final ArchRule domainHasNoFrameworkDeps = noClasses()
		.that()
		.resideInAPackage("com.example.orders..domain..")
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage("org.springframework..", "java.sql..", "jakarta.persistence..", "com.fasterxml..",
				"tools.jackson..")
		.because("domain models must stay plain Java and independent of framework/persistence types")
		.allowEmptyShould(false);

	@ArchTest
	static final ArchRule domainDoesNotDependOnOuterLayers = noClasses()
		.that()
		.resideInAPackage("com.example.orders..domain..")
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage("com.example.orders..application..", "com.example.orders..infrastructure..",
				"com.example.orders..api..")
		.because("dependencies point inward: api/infrastructure -> application -> domain")
		.allowEmptyShould(false);

	@ArchTest
	static final ArchRule domainDoesNotDependOnProjections = noClasses()
		.that()
		.resideInAPackage("com.example.orders..domain..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage("com.example.orders.projections..")
		.because("projections consume application views and integration events, never domain models")
		.allowEmptyShould(false);

	@ArchTest
	static final ArchRule salesDomainIsOnlyUsedInsideSales = noClasses()
		.that()
		.resideOutsideOfPackage("com.example.orders.sales..")
		.and()
		.resideOutsideOfPackage("com.example.orders.sharedkernel..")
		.and()
		.resideOutsideOfPackage("com.example.orders.bootstrap..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage("com.example.orders.sales.domain..")
		.because("other contexts may only reach Sales through its application services or integration events")
		.allowEmptyShould(false);

	@ArchTest
	static final ArchRule inventoryDomainIsOnlyUsedInsideInventory = noClasses()
		.that()
		.resideOutsideOfPackage("com.example.orders.inventory..")
		.and()
		.resideOutsideOfPackage("com.example.orders.sharedkernel..")
		.and()
		.resideOutsideOfPackage("com.example.orders.bootstrap..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage("com.example.orders.inventory.domain..")
		.because("other contexts may only reach Inventory through its application services or integration events")
		.allowEmptyShould(false);

	@ArchTest
	static final ArchRule billingDomainIsOnlyUsedInsideBilling = noClasses()
		.that()
		.resideOutsideOfPackage("com.example.orders.billing..")
		.and()
		.resideOutsideOfPackage("com.example.orders.sharedkernel..")
		.and()
		.resideOutsideOfPackage("com.example.orders.bootstrap..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage("com.example.orders.billing.domain..")
		.because("other contexts may only reach Billing through its application services or integration events")
		.allowEmptyShould(false);

	@ArchTest
	static final ArchRule fulfillmentDomainIsOnlyUsedInsideFulfillment = noClasses()
		.that()
		.resideOutsideOfPackage("com.example.orders.fulfillment..")
		.and()
		.resideOutsideOfPackage("com.example.orders.sharedkernel..")
		.and()
		.resideOutsideOfPackage("com.example.orders.bootstrap..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage("com.example.orders.fulfillment.domain..")
		.because("other contexts may only reach Fulfillment through its application services or integration events")
		.allowEmptyShould(false);

	@ArchTest
	static final ArchRule sharedKernelDoesNotDependOnContexts = noClasses()
		.that()
		.resideInAPackage("com.example.orders..sharedkernel..")
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage("com.example.orders..sales..", "com.example.orders..inventory..",
				"com.example.orders..billing..", "com.example.orders..fulfillment..",
				"com.example.orders..projections..", "com.example.orders..examples..")
		.because("the shared kernel is the published language and must not depend on bounded contexts")
		.allowEmptyShould(false);

	@ArchTest
	static final ArchRule eventSourcingExampleIsStandalone = noClasses()
		.that()
		.resideInAPackage("com.example.orders..examples.eventsourcing..")
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage("org.springframework..")
		.because("the event-sourcing example is a self-contained, framework-free teaching example")
		.allowEmptyShould(false);

}
