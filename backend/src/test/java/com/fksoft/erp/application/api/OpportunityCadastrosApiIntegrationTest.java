package com.fksoft.erp.application.api;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fksoft.erp.AbstractIntegrationTest;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.repository.OpportunityRepository;
import com.fksoft.erp.domain.crm.repository.OriginRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end (MockMvc, real Postgres) of the Opportunity cadastros (activity type/result, loss reason):
 * each new CRUD endpoint is wired and seeded with the former enum values, and the referencing flow honors
 * the cadastro — an inactive (soft-deleted) activity type can no longer be used to register an activity
 * (422), mirroring the unknown-id rejection. The generic CRUD/duplicate/scope contract is covered by
 * {@link ReferenceApiIntegrationTest} (these cadastros share the same base controller/service).
 */
class OpportunityCadastrosApiIntegrationTest extends AbstractIntegrationTest {

    private static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private OpportunityRepository opportunities;

    @Autowired
    private LeadRepository leads;

    @Autowired
    private OriginRepository origins;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void listsTheSeededActivityTypes() throws Exception {
        mvc.perform(get("/api/crm/opportunity-activity-types").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItem("PHONE_CALL")))
                .andExpect(jsonPath("$[*].label", hasItem("Ligação")));
    }

    @Test
    void listsTheSeededActivityResults() throws Exception {
        mvc.perform(get("/api/crm/opportunity-activity-results").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItem("CLIENT_ENGAGED")));
    }

    @Test
    void listsTheSeededLossReasons() throws Exception {
        mvc.perform(get("/api/crm/opportunity-loss-reasons").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItem("NO_BUDGET")));
    }

    @Test
    void rejectsRegisteringWithAnInactiveActivityType() throws Exception {
        String token = token();
        // Create + deactivate a throwaway activity type, then try to use it.
        String created = mvc.perform(post("/api/crm/opportunity-activity-types")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"TEMP_TYPE\",\"label\":\"Temporário\",\"sortOrder\":99}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String typeId = JsonPath.read(created, "$.id");
        mvc.perform(delete("/api/crm/opportunity-activity-types/" + typeId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        UUID opp = insertOpportunity();
        UUID resultId = UUID.fromString(jdbc.queryForObject(
                "SELECT id::text FROM opportunity_activity_results WHERE code = 'OTHER'", String.class));
        String body =
                "{\"typeId\":\"%s\",\"resultId\":\"%s\",\"description\":\"x\",\"occurredAt\":\"2026-06-10T10:00:00Z\"}"
                        .formatted(typeId, resultId);
        mvc.perform(post("/api/opportunities/" + opp + "/activities")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("opportunity.activity-type-not-available"));
    }

    private UUID insertOpportunity() {
        opportunities.deleteAll();
        leads.deleteAll();
        UUID originId = origins.findByActiveTrueOrderBySortOrderAsc().get(0).id();
        UUID leadId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO leads (id, name, phone, whatsapp, email, origin_id, status,
                                   responsible_person_id, loss_reason_id, created_at, updated_at,
                                   created_by, updated_by)
                VALUES (cast(? as uuid), 'Lead Cad', '11955550000', NULL, NULL, cast(? as uuid), 'NEW',
                        cast(? as uuid), NULL, now(), now(), cast(? as uuid), cast(? as uuid))
                """,
                leadId.toString(),
                originId.toString(),
                MANAGER.toString(),
                MANAGER.toString(),
                MANAGER.toString());
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO opportunities (id, version, lead_id, name, origin_id, responsible_person_id,
                                           main_interest, stage, created_by, updated_by)
                VALUES (cast(? as uuid), 0, cast(? as uuid), 'Cad', cast(? as uuid), cast(? as uuid),
                        'Interesse', 'NEW_OPPORTUNITY', cast(? as uuid), cast(? as uuid))
                """,
                id.toString(),
                leadId.toString(),
                originId.toString(),
                MANAGER.toString(),
                MANAGER.toString(),
                MANAGER.toString());
        return id;
    }

    private String token() throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"comercial\",\"password\":\"comercial123\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
