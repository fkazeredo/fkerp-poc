package com.fksoft.erp;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Base for integration tests: boots the full application (with security) against the shared
 * Testcontainers PostgreSQL instance and builds a security-aware {@link MockMvc}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate referenceJdbc;

    protected MockMvc mvc;

    @BeforeEach
    void setUpMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    /**
     * Resolves the seeded id of a Proposal item type cadastro value by its (stable) code, for building
     * id-based item request bodies.
     *
     * @param code the item-type code (e.g. {@code TRAVEL_PACKAGE})
     * @return the item-type id as a string
     */
    protected String proposalItemTypeId(String code) {
        return referenceJdbc.queryForObject(
                "SELECT id::text FROM proposal_item_types WHERE code = ?", String.class, code);
    }
}
