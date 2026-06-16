package com.fksoft.erp.application.api;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fksoft.erp.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * End-to-end (MockMvc, real Postgres) of the public version endpoint: it is reachable without
 * authentication and returns a SemVer {@code MAJOR.MINOR.PATCH} string (so the UI can display it).
 */
class VersionApiIntegrationTest extends AbstractIntegrationTest {

    @Test
    void exposesTheApplicationVersionWithoutAuthentication() throws Exception {
        mvc.perform(get("/api/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version", matchesPattern("\\d+\\.\\d+\\.\\d+")));
    }
}
