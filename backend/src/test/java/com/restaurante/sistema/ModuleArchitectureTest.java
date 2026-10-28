package com.restaurante.sistema;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Regras do monolito modular: os modulos de negocio nao podem formar ciclos de
 * dependencia. Quando um modulo precisa reagir ao outro no sentido contrario,
 * usa evento de dominio (common.event) ou uma interface ("port") definida por
 * quem precisa e implementada por quem sabe responder.
 */
@AnalyzeClasses(packages = "com.restaurante.sistema", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleArchitectureTest {

    @ArchTest
    static final ArchRule modulesAreFreeOfCycles =
            slices().matching("com.restaurante.sistema.modules.(*)..").should().beFreeOfCycles();
}
