package com.fksoft.erp.application.api;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fksoft.erp.AbstractIntegrationTest;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.repository.OpportunityRepository;
import com.fksoft.erp.domain.crm.repository.OriginRepository;
import com.fksoft.erp.domain.identity.AuthenticatedUser;
import com.fksoft.erp.infra.security.TokenService;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end (MockMvc, real Postgres) of the Opportunity detail consultation and the "mark as lost"
 * transition: the read model (source Lead traceability + commercial data), per-record visibility
 * (404/403), the read/update scopes, and the loss outcome. The detail exposes commercial pipeline data
 * only — never Proposal, Sale, Booking, Financial or Commission data. Opportunities (and their source
 * leads) are inserted via JDBC to set up varied owners/stages cheaply.
 */
class OpportunityDetailApiIntegrationTest extends AbstractIntegrationTest {

    private static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-000000000001");
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
    private String originLabel;
    private int phoneSeq;

    private UUID managerOpp;
    private UUID repOpp;
    private UUID lostOpp;

    @BeforeEach
    void seed() {
        opportunities.deleteAll(); // FK to leads — clear opportunities first
        leads.deleteAll();
        var origin = origins.findByActiveTrueOrderBySortOrderAsc().get(0);
        originId = origin.id();
        originLabel = origin.label();
        phoneSeq = 0;
        managerOpp = insertOpportunity("Aurora", "NEW_OPPORTUNITY", MANAGER, new BigDecimal("5000.00"));
        repOpp = insertOpportunity("Beta", "NEW_OPPORTUNITY", REPRESENTANTE, new BigDecimal("1500.00"));
        lostOpp = insertOpportunity("Gamma", "LOST", MANAGER, null);
    }

    @Test
    void opensDetailWithSourceLeadAndCommercialData() throws Exception {
        mvc.perform(get("/api/opportunities/" + managerOpp).header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(managerOpp.toString()))
                .andExpect(jsonPath("$.name").value("Aurora"))
                .andExpect(jsonPath("$.stage").value("NEW_OPPORTUNITY"))
                .andExpect(jsonPath("$.responsibleName").value("comercial"))
                .andExpect(jsonPath("$.unassigned").value(false))
                .andExpect(jsonPath("$.origin").value(originLabel))
                .andExpect(jsonPath("$.mainInterest").value("Interesse Aurora"))
                .andExpect(jsonPath("$.estimatedValue").value(notNullValue()))
                // Source Lead stays traceable.
                .andExpect(jsonPath("$.sourceLead.id").value(notNullValue()))
                .andExpect(jsonPath("$.sourceLead.name").value("Lead Aurora"))
                .andExpect(jsonPath("$.sourceLead.email").value("aurora@example.com"))
                .andExpect(jsonPath("$.sourceLead.status").value("NEW"))
                // Not lost → no loss; activity/stage history reserved (empty) for now.
                .andExpect(jsonPath("$.loss").isEmpty())
                .andExpect(jsonPath("$.activities").isEmpty())
                .andExpect(jsonPath("$.stageHistory").isEmpty());
    }

    @Test
    void representativeOpensOwnOpportunity() throws Exception {
        String rep = login("representante", "representante123");
        mvc.perform(get("/api/opportunities/" + repOpp).header("Authorization", "Bearer " + rep))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Beta"));
    }

    @Test
    void forbidsDetailOfAnOpportunityTheUserCannotSee() throws Exception {
        // A representative (own-only) may not open the manager's Opportunity.
        String rep = login("representante", "representante123");
        mvc.perform(get("/api/opportunities/" + managerOpp).header("Authorization", "Bearer " + rep))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("opportunity.access-denied"));
    }

    @Test
    void returnsNotFoundForUnknownOpportunity() throws Exception {
        mvc.perform(get("/api/opportunities/" + UUID.randomUUID()).header("Authorization", "Bearer " + manager()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("opportunity.not-found"));
    }

    @Test
    void rejectsDetailWithoutAReadScope() throws Exception {
        String noRead = tokens.issueAccessToken(new AuthenticatedUser(MANAGER, "m", Set.of("crm:opportunity:create")));
        mvc.perform(get("/api/opportunities/" + managerOpp).header("Authorization", "Bearer " + noRead))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnauthenticatedDetail() throws Exception {
        mvc.perform(get("/api/opportunities/" + managerOpp)).andExpect(status().isUnauthorized());
    }

    @Test
    void marksAsLostWithAReasonAndShowsLoss() throws Exception {
        String body = "{\"lossReasonId\":\"%s\",\"note\":\"Cliente fechou com concorrente\"}"
                .formatted(lossReasonId("COMPETITOR_CHOSEN"));
        mvc.perform(post("/api/opportunities/" + managerOpp + "/lose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("LOST"))
                .andExpect(jsonPath("$.loss.reason").value("Escolheu concorrente"))
                .andExpect(jsonPath("$.loss.lostBy").value("comercial"))
                .andExpect(jsonPath("$.loss.lostAt").value(notNullValue()))
                .andExpect(jsonPath("$.loss.note").value("Cliente fechou com concorrente"));
    }

    @Test
    void rejectsLosingAnAlreadyLostOpportunity() throws Exception {
        mvc.perform(post("/api/opportunities/" + lostOpp + "/lose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lossReasonId\":\"%s\"}".formatted(lossReasonId("NO_BUDGET")))
                        .header("Authorization", "Bearer " + manager()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("opportunity.cannot-mark-lost"));
    }

    @Test
    void rejectsLoseWithUnknownReasonId() throws Exception {
        mvc.perform(post("/api/opportunities/" + managerOpp + "/lose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lossReasonId\":\"%s\"}".formatted(UUID.randomUUID()))
                        .header("Authorization", "Bearer " + manager()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("opportunity.loss-reason-not-available"));
    }

    @Test
    void rejectsLoseWithoutAReason() throws Exception {
        mvc.perform(post("/api/opportunities/" + managerOpp + "/lose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", "Bearer " + manager()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsLoseWithoutTheUpdateScope() throws Exception {
        // diretor (board) consults every Opportunity but holds no operation scope → 403 at the gate.
        String dir = login("diretor", "diretor123");
        mvc.perform(post("/api/opportunities/" + managerOpp + "/lose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lossReasonId\":\"%s\"}".formatted(lossReasonId("OTHER")))
                        .header("Authorization", "Bearer " + dir))
                .andExpect(status().isForbidden());
    }

    @Test
    void representativeCannotLoseAnotherUsersOpportunity() throws Exception {
        // The representative holds crm:opportunity:update but cannot see the manager's Opportunity.
        String rep = login("representante", "representante123");
        mvc.perform(post("/api/opportunities/" + managerOpp + "/lose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lossReasonId\":\"%s\"}".formatted(lossReasonId("OTHER")))
                        .header("Authorization", "Bearer " + rep))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("opportunity.access-denied"));
    }

    private UUID lossReasonId(String code) {
        return UUID.fromString(jdbc.queryForObject(
                "SELECT id::text FROM opportunity_loss_reasons WHERE code = ?", String.class, code));
    }

    private UUID insertOpportunity(String name, String stage, UUID responsibleId, BigDecimal value) {
        UUID leadId = insertLead(name, responsibleId);
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO opportunities (id, version, lead_id, name, origin_id, responsible_person_id,
                                           main_interest, stage, estimated_value, loss_reason,
                                           created_by, updated_by)
                VALUES (cast(? as uuid), 0, cast(? as uuid), ?, cast(? as uuid), cast(? as uuid),
                        ?, ?, ?, ?, cast(? as uuid), cast(? as uuid))
                """,
                id.toString(),
                leadId.toString(),
                name,
                originId.toString(),
                responsibleId == null ? null : responsibleId.toString(),
                "Interesse " + name,
                stage,
                value,
                "LOST".equals(stage) ? "OTHER" : null,
                MANAGER.toString(),
                MANAGER.toString());
        return id;
    }

    private UUID insertLead(String name, UUID responsibleId) {
        UUID id = UUID.randomUUID();
        String phone = "1191000%04d".formatted(phoneSeq++);
        jdbc.update(
                """
                INSERT INTO leads (id, name, phone, whatsapp, email, origin_id, status,
                                   responsible_person_id, loss_reason_id, created_at, updated_at,
                                   created_by, updated_by)
                VALUES (cast(? as uuid), ?, ?, NULL, ?, cast(? as uuid), 'NEW', cast(? as uuid),
                        NULL, now(), now(), cast(? as uuid), cast(? as uuid))
                """,
                id.toString(),
                "Lead " + name,
                phone,
                name.toLowerCase() + "@example.com",
                originId.toString(),
                responsibleId == null ? null : responsibleId.toString(),
                MANAGER.toString(),
                MANAGER.toString());
        return id;
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
