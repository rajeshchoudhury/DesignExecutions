package com.microservices.principles;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * ArchUnit tests that enforce architectural constraints as executable code.
 *
 * <h3>TDD Principle — Architecture as Tests</h3>
 * <p>Architecture rules are often documented in wikis that nobody reads and
 * everyone violates. ArchUnit turns these rules into <em>tests</em> that run
 * in CI/CD — a violation fails the build, not a code review comment.</p>
 *
 * <h3>SOC Principle — Enforced by Tests</h3>
 * <p>The layered architecture test ensures that controllers never bypass the
 * service layer to access repositories directly, and that the domain layer
 * has no dependency on Spring framework classes.</p>
 */
@DisplayName("Architecture Rules")
class ArchitectureTest {

    private static JavaClasses importedClasses;

    @BeforeAll
    static void setUp() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.microservices.principles");
    }

    @Nested
    @DisplayName("Layered Architecture")
    class LayeredArchitectureRules {

        @Test
        @DisplayName("should enforce layer dependencies: controller -> service -> repository -> domain")
        void shouldEnforceLayeredArchitecture() {
            layeredArchitecture()
                    .consideringAllDependencies()
                    .layer("Controller").definedBy("..controller..")
                    .layer("Service").definedBy("..service..")
                    .layer("Repository").definedBy("..repository..")
                    .layer("Domain").definedBy("..domain..")
                    .layer("DTO").definedBy("..dto..")
                    .layer("Config").definedBy("..config..")

                    .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
                    .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller", "Config")
                    .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service")

                    .check(importedClasses);
        }
    }

    @Nested
    @DisplayName("Domain Layer Purity")
    class DomainLayerRules {

        @Test
        @DisplayName("domain entities should not depend on Spring framework")
        void domainShouldNotDependOnSpring() {
            noClasses()
                    .that().resideInAPackage("..domain.entity..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("org.springframework..")
                    .because("Domain entities must be framework-agnostic (SOC principle)")
                    .check(importedClasses);
        }

        @Test
        @DisplayName("domain should not depend on DTOs")
        void domainShouldNotDependOnDtos() {
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..dto..")
                    .because("Domain must not know about API contracts (SOC principle)")
                    .check(importedClasses);
        }
    }

    @Nested
    @DisplayName("Naming Conventions")
    class NamingConventions {

        @Test
        @DisplayName("controllers should be suffixed with 'Controller'")
        void controllersShouldBeSuffixed() {
            classes()
                    .that().resideInAPackage("..controller..")
                    .and().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                    .should().haveSimpleNameEndingWith("Controller")
                    .check(importedClasses);
        }

        @Test
        @DisplayName("service implementations should be suffixed with 'Impl'")
        void serviceImplsShouldBeSuffixed() {
            classes()
                    .that().resideInAPackage("..service.impl..")
                    .should().haveSimpleNameEndingWith("Impl")
                    .check(importedClasses);
        }

        @Test
        @DisplayName("repositories should be suffixed with 'Repository'")
        void repositoriesShouldBeSuffixed() {
            classes()
                    .that().resideInAPackage("..repository..")
                    .and().areInterfaces()
                    .should().haveSimpleNameEndingWith("Repository")
                    .orShould().haveSimpleNameEndingWith("Specifications")
                    .check(importedClasses);
        }
    }
}
