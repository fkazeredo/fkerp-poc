package com.fksoft.erp.application.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fksoft.erp.AbstractIntegrationTest;
import com.fksoft.erp.domain.crm.model.OpportunityStage;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.repository.OpportunityRepository;
import com.fksoft.erp.domain.crm.repository.OriginRepository;
import com.fksoft.erp.domain.identity.AuthenticatedUser;
import com.fksoft.erp.infra.security.TokenService;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/**
 * End-to-end (MockMvc, real Postgres) of the operational Opportunity list: profile-based visibility
 * (own / own+pool / all), the lost-by-default rule (and lost only when explicitly filtered), the
 * {@code PageResponse} shape, the read scopes (Finance has none → 403) and that filters cannot bypass
 * visibility. The list exposes commercial pipeline data only — never Proposal, Sale, Booking or
 * Financial fields. Opportunities (and their source leads) are inserted directly via JDBC to set up
 * varied stages/owners cheaply; the qualified-lead → create flow is covered by
 * {@code OpportunityCreationApiIntegrationTest}.
 */
class OpportunityListApiIntegrationTest extends AbstractIntegrationTest {

    private static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID VENDEDOR = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID REPRESENTANTE = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Autowired
    private OpportunityRepository opportunities;

    @Autowired
    private LeadRepository leads;

    @Autowired
    private OriginRepository origins;

    @Autowired
    private TokenService tokens;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID originId;
    private int phoneSeq;

    @BeforeEach
    void seed() {
        opportunities.deleteAll(); // FK to leads — clear opportunities first
        leads.deleteAll();
        originId = origins.findByActiveTrueOrderBySortOrderAsc().get(0).id();
        phoneSeq = 0;
        insertOpportunity("Alpha", OpportunityStage.NEW_OPPORTUNITY, REPRESENTANTE, new BigDecimal("1000.00"));
        insertOpportunity("Bravo", OpportunityStage.DISCOVERY, MANAGER, new BigDecimal("2500.00"));
        insertOpportunity("Charlie", OpportunityStage.LOST, MANAGER, null);
        insertOpportunity("Delta", OpportunityStage.NEW_OPPORTUNITY, null, null);
        insertOpportunity("Echo", OpportunityStage.PRODUCT_FIT, VENDEDOR, null);
    }

    @Test
    void listsForAuthorizedUserAndExcludesLostByDefault() throws Exception {
        mvc.perform(get("/api/opportunities").header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", hasItems("Alpha", "Bravo", "Delta", "Echo")))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("Charlie"))))
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void showsLostOnlyWhenExplicitlyFiltered() throws Exception {
        mvc.perform(get("/api/opportunities").param("stage", "LOST").header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", hasItem("Charlie")))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("Alpha"))));
    }

    @Test
    void managerSeesEveryOpportunity() throws Exception {
        mvc.perform(get("/api/opportunities").header("Authorization", "Bearer " + manager()))
                .andExpect(jsonPath("$.content[*].name", hasItems("Alpha", "Bravo", "Delta", "Echo")));
    }

    @Test
    void representativeSeesOnlyOwnOpportunities() throws Exception {
        String rep = login("representante", "representante123");
        mvc.perform(get("/api/opportunities").header("Authorization", "Bearer " + rep))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", hasItem("Alpha")))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("Bravo"))))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("Delta"))))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("Echo"))));
    }

    @Test
    void sellerSeesOwnPlusTheUnassignedPool() throws Exception {
        String seller = login("vendedor", "vendedor123");
        mvc.perform(get("/api/opportunities").header("Authorization", "Bearer " + seller))
                .andExpect(jsonPath("$.content[*].name", hasItems("Echo", "Delta")))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("Alpha"))))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("Bravo"))));
    }

    @Test
    void directorConsultsEveryOpportunity() throws Exception {
        String dir = login("diretor", "diretor123");
        mvc.perform(get("/api/opportunities").header("Authorization", "Bearer " + dir))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", hasItems("Alpha", "Bravo", "Delta", "Echo")));
    }

    @Test
    void filtersDoNotBypassVisibility() throws Exception {
        // An own-only representative filtering by LOST must not surface the manager's lost Opportunity.
        String rep = login("representante", "representante123");
        mvc.perform(get("/api/opportunities").param("stage", "LOST").header("Authorization", "Bearer " + rep))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", not(hasItem("Charlie"))));
    }

    @Test
    void filtersByResponsible() throws Exception {
        String token = manager();
        // A specific responsible person.
        mvc.perform(get("/api/opportunities")
                        .param("responsible", VENDEDOR.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", hasItem("Echo")))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("Alpha"))));
        // The unassigned pool.
        mvc.perform(get("/api/opportunities")
                        .param("responsible", "unassigned")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.content[*].name", hasItem("Delta")))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("Echo"))));
    }

    @Test
    void filtersByOrigin() throws Exception {
        // Move Bravo to a second origin; filtering by it returns only Bravo.
        UUID secondOrigin = origins.findByActiveTrueOrderBySortOrderAsc().get(1).id();
        jdbc.update(
                "UPDATE opportunities SET origin_id = cast(? as uuid) WHERE name = 'Bravo'", secondOrigin.toString());
        mvc.perform(get("/api/opportunities")
                        .param("originId", secondOrigin.toString())
                        .header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", hasItem("Bravo")))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("Alpha"))));
    }

    @Test
    void filtersByCreationPeriod() throws Exception {
        jdbc.update("UPDATE opportunities SET created_at = TIMESTAMPTZ '2026-01-10 12:00:00+00' WHERE name = 'Alpha'");
        jdbc.update("UPDATE opportunities SET created_at = TIMESTAMPTZ '2026-03-20 12:00:00+00' WHERE name = 'Bravo'");
        mvc.perform(get("/api/opportunities")
                        .param("createdFrom", "2026-01-01")
                        .param("createdTo", "2026-01-31")
                        .header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", hasItem("Alpha")))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("Bravo"))));
    }

    @Test
    void filtersByExpectedClosePeriod() throws Exception {
        jdbc.update("UPDATE opportunities SET expected_close_date = DATE '2026-02-15' WHERE name = 'Alpha'");
        jdbc.update("UPDATE opportunities SET expected_close_date = DATE '2026-05-15' WHERE name = 'Bravo'");
        mvc.perform(get("/api/opportunities")
                        .param("closeFrom", "2026-02-01")
                        .param("closeTo", "2026-02-28")
                        .header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", hasItem("Alpha")))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("Bravo"))));
    }

    @Test
    void filtersByEstimatedValueRange() throws Exception {
        String token = manager(); // seeded values: Alpha=1000.00, Bravo=2500.00
        mvc.perform(get("/api/opportunities").param("valueMin", "2000").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", hasItem("Bravo")))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("Alpha"))));
        mvc.perform(get("/api/opportunities")
                        .param("valueMin", "500")
                        .param("valueMax", "1500")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.content[*].name", hasItem("Alpha")))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("Bravo"))));
    }

    @Test
    void searchesByLeadNameContactAndSummary() throws Exception {
        // Give Alpha's source lead a distinctive contact and the Opportunity a distinctive summary.
        jdbc.update("UPDATE leads SET email = 'zephyr@example.com' WHERE name = 'Lead Alpha'");
        jdbc.update("UPDATE opportunities SET main_interest = 'Zephyr rooftop package' WHERE name = 'Alpha'");
        String token = manager();
        // By the source Lead's name (correlated EXISTS subquery).
        search(token, "Lead Alpha").andExpect(jsonPath("$.content[*].name", hasItem("Alpha")));
        // By the source Lead's contact (email) — the contact lives on the Lead, not the Opportunity.
        search(token, "zephyr@example.com").andExpect(jsonPath("$.content[*].name", hasItem("Alpha")));
        // By the Opportunity's own summary (main interest).
        search(token, "rooftop").andExpect(jsonPath("$.content[*].name", hasItem("Alpha")));
        // A non-matching term finds nothing.
        search(token, "no-such-term").andExpect(jsonPath("$.content[*].name", not(hasItem("Alpha"))));
    }

    @Test
    void responsibleFilterDoesNotBypassVisibility() throws Exception {
        // An own-only representative filtering by the manager's id must not surface the manager's Opportunities.
        String rep = login("representante", "representante123");
        mvc.perform(get("/api/opportunities")
                        .param("responsible", MANAGER.toString())
                        .header("Authorization", "Bearer " + rep))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", not(hasItem("Bravo"))));
    }

    @Test
    void paginatesWithEnvelope() throws Exception {
        mvc.perform(get("/api/opportunities").param("size", "2").header("Authorization", "Bearer " + manager()))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void exposesOnlyCommercialFieldsAndReservesActivityColumns() throws Exception {
        String body = mvc.perform(get("/api/opportunities").header("Authorization", "Bearer " + manager()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Map<String, Object> item = JsonPath.read(body, "$.content[0]");
        // The contract is exactly these fields — no Proposal / Sale / Booking / Financial / Commission data.
        assertThat(item.keySet())
                .containsExactlyInAnyOrder(
                        "id",
                        "leadId",
                        "name",
                        "responsibleId",
                        "responsibleName",
                        "unassigned",
                        "stage",
                        "estimatedValue",
                        "expectedCloseDate",
                        "createdAt",
                        "lastActivityAt",
                        "nextActionDate");
        // Reserved for the future Opportunity-activities slice — present but null for now.
        assertThat(item.get("lastActivityAt")).isNull();
        assertThat(item.get("nextActionDate")).isNull();
    }

    @Test
    void financeHasNoAccessToOpportunities() throws Exception {
        String fin = login("financeiro", "financeiro123");
        mvc.perform(get("/api/opportunities").header("Authorization", "Bearer " + fin))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsWithoutAReadScope() throws Exception {
        // Holding only the create scope does not grant listing.
        String noRead = tokens.issueAccessToken(new AuthenticatedUser(VENDEDOR, "v", Set.of("crm:opportunity:create")));
        mvc.perform(get("/api/opportunities").header("Authorization", "Bearer " + noRead))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnauthenticated() throws Exception {
        mvc.perform(get("/api/opportunities")).andExpect(status().isUnauthorized());
    }

    private void insertOpportunity(String name, OpportunityStage stage, UUID responsibleId, BigDecimal estimatedValue) {
        UUID leadId = insertLead(name, responsibleId);
        jdbc.update(
                """
                INSERT INTO opportunities (id, version, lead_id, name, origin_id, responsible_person_id,
                                           main_interest, stage, estimated_value, created_by, updated_by)
                VALUES (cast(? as uuid), 0, cast(? as uuid), ?, cast(? as uuid), cast(? as uuid),
                        ?, ?, ?, cast(? as uuid), cast(? as uuid))
                """,
                UUID.randomUUID().toString(),
                leadId.toString(),
                name,
                originId.toString(),
                responsibleId == null ? null : responsibleId.toString(),
                "Pacote " + name,
                stage.name(),
                estimatedValue,
                MANAGER.toString(),
                MANAGER.toString());
    }

    private UUID insertLead(String name, UUID responsibleId) {
        UUID id = UUID.randomUUID();
        String phone = "1190000%04d".formatted(phoneSeq++);
        jdbc.update(
                """
                INSERT INTO leads (id, name, phone, whatsapp, email, origin_id, status,
                                   responsible_person_id, loss_reason_id, created_at, updated_at,
                                   created_by, updated_by)
                VALUES (cast(? as uuid), ?, ?, NULL, NULL, cast(? as uuid), 'NEW', cast(? as uuid),
                        NULL, now(), now(), cast(? as uuid), cast(? as uuid))
                """,
                id.toString(),
                "Lead " + name,
                phone,
                originId.toString(),
                responsibleId == null ? null : responsibleId.toString(),
                MANAGER.toString(),
                MANAGER.toString());
        return id;
    }

    private ResultActions search(String token, String term) throws Exception {
        return mvc.perform(get("/api/opportunities").param("q", term).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private String manager() throws Exception {
        return login("comercial", "comercial123");
    }

    private String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
