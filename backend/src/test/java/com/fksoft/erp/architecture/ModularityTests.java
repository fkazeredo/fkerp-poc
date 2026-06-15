package com.fksoft.erp.architecture;

import com.fksoft.erp.ErpApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Verifies inter-module boundaries (Spring Modulith). With explicitly-annotated detection and no
 * declared modules yet, this passes trivially and starts enforcing as soon as modules appear.
 */
class ModularityTests {

    @Test
    void verifiesModularStructure() {
        ApplicationModules.of(ErpApplication.class).verify();
    }
}
