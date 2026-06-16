package com.fksoft.erp.application.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fksoft.erp.AbstractIntegrationTest;
import com.fksoft.erp.domain.crm.model.OpportunityStage;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.repository.LossReasonRepository;
import com.fksoft.erp.domain.crm.repository.OpportunityRepository;
import com.fksoft.erp.domain.crm.repository.OriginRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end (MockMvc, real Postgres) of the minimum commercial pipeline: moving an Opportunity between
 * active stages (free movement), the recorded stage-movement history, the rules around LOST (terminal,
 * reached only through the lose action) and the {@code crm:opportunity:update} scope + visibility checks.
 */
class OpportunityStageApiIntegrationTest extends AbstractIntegrationTest {

    private static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID REPRESENTANTE = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Autowired
    private OpportunityRepository opportunities;

    @Autowired
    private LeadRepository leads;

    @Autowired
    private OriginRepository origins;

    @Autowired
    private LossReasonRepository lossReasons;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID originId;
    private UUID lossReasonId;
    private int phoneSeq;

    private UUID managerOpp;
    private UUID repOpp;
    private UUID lostOpp;

    @BeforeEach
    void seed() {
        opportunities.deleteAll(); // cascades to stage changes via JPA
        leads.deleteAll();
        originId = origins.findByActiveTrueOrderBySortOrderAsc().get(0).id();
        lossReasonId = lossReasons.findByActiveTrueOrderBySortOrderAsc().get(0).id();
        phoneSeq = 0;
        managerOpp = insertOpportunity("Aurora", OpportunityStage.NEW_OPPORTUNITY, MANAGER);
        repOpp = insertOpportunity("Beta", OpportunityStage.NEW_OPPORTUNITY, REPRESENTANTE);
        lostOpp = insertOpportunity("Gamma", OpportunityStage.LOST, MANAGER);
    }

    @Test
    void movesToAnActiveStageAndRecordsTheMovement() throws Exception {
        changeStage(managerOpp, "DISCOVERY", manager())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("DISCOVERY"))
                .andExpect(jsonPath("$.stageHistory[0].from").value("NEW_OPPORTUNITY"))
                .andExpect(jsonPath("$.stageHistory[0].to").value("DISCOVERY"))
                .andExpect(jsonPath("$.stageHistory[0].by").value("comercial"));
    }

    @Test
    void advancesThroughTheWholeFunnel() throws Exception {
        String token = manager();
        changeStage(managerOpp, "DISCOVERY", token).andExpect(status().isOk());
        changeStage(managerOpp, "PRODUCT_FIT", token).andExpect(status().isOk());
        changeStage(managerOpp, "READY_FOR_PROPOSAL", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("READY_FOR_PROPOSAL"))
                .andExpect(jsonPath("$.stageHistory.length()").value(3))
                .andExpect(jsonPath("$.stageHistory[0].to").value("READY_FOR_PROPOSAL"));
    }

    @Test
    void rejectsMovingBackward() throws Exception {
        String token = manager();
        changeStage(managerOpp, "DISCOVERY", token).andExpect(status().isOk());
        changeStage(managerOpp, "NEW_OPPORTUNITY", token)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("opportunity.invalid-stage-transition"));
    }

    @Test
    void rejectsSkippingAStage() throws Exception {
        changeStage(managerOpp, "PRODUCT_FIT", manager())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("opportunity.invalid-stage-transition"));
    }

    @Test
    void representativeMovesOwnOpportunity() throws Exception {
        String rep = login("representante", "representante123");
        changeStage(repOpp, "DISCOVERY", rep)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("DISCOVERY"));
    }

    @Test
    void rejectsMovingToLostThroughTheStageEndpoint() throws Exception {
        changeStage(managerOpp, "LOST", manager())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("opportunity.invalid-stage-transition"));
    }

    @Test
    void rejectsMovingAStageFromLost() throws Exception {
        changeStage(lostOpp, "DISCOVERY", manager())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("opportunity.invalid-stage-transition"));
    }

    @Test
    void rejectsMovingToTheSameStage() throws Exception {
        changeStage(managerOpp, "NEW_OPPORTUNITY", manager())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("opportunity.invalid-stage-transition"));
    }

    @Test
    void rejectsStageChangeWithoutTheUpdateScope() throws Exception {
        // diretor (board) consults every Opportunity but holds no operation scope → 403 at the gate.
        changeStage(managerOpp, "DISCOVERY", login("diretor", "diretor123")).andExpect(status().isForbidden());
    }

    @Test
    void representativeCannotMoveAnotherUsersOpportunity() throws Exception {
        // The representative holds crm:opportunity:update but cannot see the manager's Opportunity.
        changeStage(managerOpp, "DISCOVERY", login("representante", "representante123"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("opportunity.access-denied"));
    }

    @Test
    void rejectsStageChangeWithoutAStage() throws Exception {
        mvc.perform(post("/api/opportunities/" + managerOpp + "/stage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", "Bearer " + manager()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnUnknownStageValue() throws Exception {
        mvc.perform(post("/api/opportunities/" + managerOpp + "/stage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"BOGUS\"}")
                        .header("Authorization", "Bearer " + manager()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void losingRecordsTheMovementToLost() throws Exception {
        String token = manager();
        mvc.perform(post("/api/opportunities/" + managerOpp + "/lose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lossReasonId\":\"%s\"}".formatted(lossReasonId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("LOST"))
                .andExpect(jsonPath("$.stageHistory[0].from").value("NEW_OPPORTUNITY"))
                .andExpect(jsonPath("$.stageHistory[0].to").value("LOST"));
        // And it is durable on the detail.
        mvc.perform(get("/api/opportunities/" + managerOpp).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.stageHistory[0].to").value("LOST"));
    }

    private org.springframework.test.web.servlet.ResultActions changeStage(UUID id, String stage, String token)
            throws Exception {
        return mvc.perform(post("/api/opportunities/" + id + "/stage")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stage\":\"%s\"}".formatted(stage))
                .header("Authorization", "Bearer " + token));
    }

    private UUID insertOpportunity(String name, OpportunityStage stage, UUID responsibleId) {
        UUID leadId = insertLead(name, responsibleId);
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO opportunities (id, version, lead_id, name, origin_id, responsible_person_id,
                                           main_interest, stage, loss_reason_id, created_by, updated_by)
                VALUES (cast(? as uuid), 0, cast(? as uuid), ?, cast(? as uuid), cast(? as uuid),
                        ?, ?, cast(? as uuid), cast(? as uuid), cast(? as uuid))
                """,
                id.toString(),
                leadId.toString(),
                name,
                originId.toString(),
                responsibleId == null ? null : responsibleId.toString(),
                "Interesse " + name,
                stage.name(),
                stage == OpportunityStage.LOST ? lossReasonId.toString() : null,
                MANAGER.toString(),
                MANAGER.toString());
        return id;
    }

    private UUID insertLead(String name, UUID responsibleId) {
        UUID id = UUID.randomUUID();
        String phone = "1192000%04d".formatted(phoneSeq++);
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
