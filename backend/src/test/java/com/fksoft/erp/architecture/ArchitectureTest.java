package com.fksoft.erp.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Executable encoding of the architecture rules from CLAUDE.md (section 15). Layer rules tolerate
 * empty class sets during the bootstrap (see {@code archunit.properties}).
 */
@AnalyzeClasses(packages = "com.fksoft.erp", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainMustNotDependOnDeliveryOrInfra = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..application..", "..infra..");

    @ArchTest
    static final ArchRule infraMustNotDependOnDelivery = noClasses()
            .that()
            .resideInAPackage("..infra..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..application..");

    @ArchTest
    static final ArchRule controllersMustNotAccessRepositories = noClasses()
            .that()
            .resideInAPackage("..api..")
            .should()
            .dependOnClassesThat()
            .haveSimpleNameEndingWith("Repository");

    @ArchTest
    static final ArchRule noImplSuffix = noClasses().should().haveSimpleNameEndingWith("Impl");

    @ArchTest
    static final ArchRule noFieldInjection =
            noFields().should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired");

    @ArchTest
    static final ArchRule exceptionsLiveWithTheirDomain =
            classes().that().haveSimpleNameEndingWith("Exception").should().resideInAPackage("..domain..");

    @ArchTest
    static final ArchRule noLombokDataOnEntities = noClasses()
            .that()
            .areAnnotatedWith("jakarta.persistence.Entity")
            .should()
            .beAnnotatedWith("lombok.Data");

    @ArchTest
    static final ArchRule noLombokSetterOnEntities = noClasses()
            .that()
            .areAnnotatedWith("jakarta.persistence.Entity")
            .should()
            .beAnnotatedWith("lombok.Setter");
}
