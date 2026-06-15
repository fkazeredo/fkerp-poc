package com.fksoft.erp;

import org.junit.jupiter.api.Test;

/**
 * Smoke test: the Spring context boots against a real PostgreSQL (Flyway runs), which also
 * exercises the {@link com.fksoft.erp.infra.config.AppProperties} startup validation.
 */
class ErpApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {}
}
