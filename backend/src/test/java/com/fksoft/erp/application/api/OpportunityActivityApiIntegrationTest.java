package com.fksoft.erp.application.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end (MockMvc, real Postgres) of commercial activities on an Opportunity: registering an
 * activity (with all required fields, referencing the activity-type/result cadastros by id), it appearing
 * in the detail (showing the cadastro label) and surfacing on the operational list (last activity + next
 * action), append-only history, validation (required fields / future date / unknown cadastro id), and the
 * {@code crm:opportunity:update} scope + visibility checks. Activities never move the stage and never create
 * Proposal/Sale/Booking/Financial data.
 */
class OpportunityActivityApiIntegrationTest extends AbstractIntegrationTest {

    private static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID REPRESENTANTE = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Autowired
    private OpportunityRepository opportunities;

    @Autowired
    private LeadRepository leads;

    @Autowired
    private OriginRepository origins;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID originId;
    private int phoneSeq;

    private UUID managerOpp;
    private UUID lostOpp;

    @BeforeEach
    void seed() {
        opportunities.deleteAll(); // cascades to activities/stage changes via JPA
        leads.deleteAll();
        originId = origins.findByActiveTrueOrderBySortOrderAsc().get(0).id();
        phoneSeq = 0;
        managerOpp = insertOpportunity("Aurora", "NEW_OPPORTUNITY", MANAGER);
        insertOpportunity("Beta", "NEW_OPPORTUNITY", REPRESENTANTE);
        lostOpp = insertOpportunity("Gamma", "LOST", MANAGER);
    }

    @Test
    void registersAnActivityAndShowsItInDetail() throws Exception {
        register(
                        managerOpp,
                        body("PHONE_CALL", "CLIENT_ENGAGED", "Ligação inicial", "2026-06-10T13:00:00Z", "2026-06-20"),
                        manager())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities[0].type").value("Ligação"))
                .andExpect(jsonPath("$.activities[0].result").value("Cliente engajado"))
                .andExpect(jsonPath("$.activities[0].description").value("Ligação inicial"))
                .andExpect(jsonPath("$.activities[0].registeredBy").value("comercial"))
                .andExpect(jsonPath("$.activities[0].nextActionDate").value("2026-06-20"))
                .andExpect(jsonPath("$.nextActionDate").value("2026-06-20"))
                // The activity does not move the stage.
                .andExpect(jsonPath("$.stage").value("NEW_OPPORTUNITY"));
    }

    @Test
    void surfacesLastActivityAndNextActionOnTheList() throws Exception {
        String token = manager();
        register(
                        managerOpp,
                        body("MEETING", "PRODUCT_FIT_IDENTIFIED", "Reunião", "2026-06-11T09:00:00Z", "2026-06-25"),
                        token)
                .andExpect(status().isOk());
        mvc.perform(get("/api/opportunities").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.name=='Aurora')].lastActivityAt")
                        .value(hasItem(notNullValue())))
                .andExpect(jsonPath("$.content[?(@.name=='Aurora')].nextActionDate")
                        .value(hasItem("2026-06-25")));
    }

    @Test
    void preservesHistoryNewestFirst() throws Exception {
        String token = manager();
        register(managerOpp, body("EMAIL", "WAITING_FOR_CLIENT", "primeiro", "2026-06-10T10:00:00Z", null), token)
                .andExpect(status().isOk());
        register(managerOpp, body("WHATSAPP", "NEEDS_FOLLOW_UP", "segundo", "2026-06-14T10:00:00Z", null), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities.length()").value(2))
                .andExpect(jsonPath("$.activities[0].description").value("segundo"))
                .andExpect(jsonPath("$.activities[1].description").value("primeiro"));
    }

    @Test
    void allowsRegisteringOnALostOpportunity() throws Exception {
        // Mirrors the Lead: an activity (e.g. a final note) may be recorded in any stage, including LOST.
        register(lostOpp, body("INTERNAL_NOTE", "NOT_INTERESTED", "encerrado", "2026-06-12T10:00:00Z", null), manager())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities[0].description").value("encerrado"));
    }

    @Test
    void rejectsMissingRequiredFields() throws Exception {
        String token = manager();
        UUID type = typeId("OTHER");
        UUID result = resultId("OTHER");
        register(
                        managerOpp,
                        "{\"resultId\":\"%s\",\"description\":\"x\",\"occurredAt\":\"2026-06-10T10:00:00Z\"}"
                                .formatted(result),
                        token)
                .andExpect(status().isBadRequest()); // missing typeId
        register(
                        managerOpp,
                        "{\"typeId\":\"%s\",\"description\":\"x\",\"occurredAt\":\"2026-06-10T10:00:00Z\"}"
                                .formatted(type),
                        token)
                .andExpect(status().isBadRequest()); // missing resultId
        register(
                        managerOpp,
                        "{\"typeId\":\"%s\",\"resultId\":\"%s\",\"occurredAt\":\"2026-06-10T10:00:00Z\"}"
                                .formatted(type, result),
                        token)
                .andExpect(status().isBadRequest()); // missing description
        register(
                        managerOpp,
                        "{\"typeId\":\"%s\",\"resultId\":\"%s\",\"description\":\"x\"}".formatted(type, result),
                        token)
                .andExpect(status().isBadRequest()); // missing occurredAt
    }

    @Test
    void rejectsAFutureDate() throws Exception {
        register(managerOpp, body("OTHER", "OTHER", "x", "2999-01-01T10:00:00Z", null), manager())
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnUnknownCadastroId() throws Exception {
        // A well-formed but unknown activity-type id is rejected at the service (422), not as malformed input.
        String body =
                "{\"typeId\":\"%s\",\"resultId\":\"%s\",\"description\":\"x\",\"occurredAt\":\"2026-06-10T10:00:00Z\"}"
                        .formatted(UUID.randomUUID(), resultId("OTHER"));
        register(managerOpp, body, manager())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("opportunity.activity-type-not-available"));
    }

    @Test
    void rejectsRegisteringWithoutTheUpdateScope() throws Exception {
        // diretor (board) consults every Opportunity but holds no operation scope → 403 at the gate.
        register(managerOpp, body("OTHER", "OTHER", "x", "2026-06-10T10:00:00Z", null), login("diretor", "diretor123"))
                .andExpect(status().isForbidden());
    }

    @Test
    void representativeCannotRegisterOnAnotherUsersOpportunity() throws Exception {
        register(
                        managerOpp,
                        body("OTHER", "OTHER", "x", "2026-06-10T10:00:00Z", null),
                        login("representante", "representante123"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("opportunity.access-denied"));
    }

    @Test
    void returnsNotFoundForUnknownOpportunity() throws Exception {
        register(UUID.randomUUID(), body("OTHER", "OTHER", "x", "2026-06-10T10:00:00Z", null), manager())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("opportunity.not-found"));
    }

    private String body(
            String typeCode, String resultCode, String description, String occurredAt, String nextActionDate) {
        String next = nextActionDate == null ? "" : ",\"nextActionDate\":\"%s\"".formatted(nextActionDate);
        return "{\"typeId\":\"%s\",\"resultId\":\"%s\",\"description\":\"%s\",\"occurredAt\":\"%s\"%s}"
                .formatted(typeId(typeCode), resultId(resultCode), description, occurredAt, next);
    }

    private UUID typeId(String code) {
        return cadastroId("opportunity_activity_types", code);
    }

    private UUID resultId(String code) {
        return cadastroId("opportunity_activity_results", code);
    }

    private UUID cadastroId(String table, String code) {
        return UUID.fromString(
                jdbc.queryForObject("SELECT id::text FROM " + table + " WHERE code = ?", String.class, code));
    }

    private org.springframework.test.web.servlet.ResultActions register(UUID id, String body, String token)
            throws Exception {
        return mvc.perform(post("/api/opportunities/" + id + "/activities")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token));
    }

    private UUID insertOpportunity(String name, String stage, UUID responsibleId) {
        UUID leadId = insertLead(name, responsibleId);
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO opportunities (id, version, lead_id, name, origin_id, responsible_person_id,
                                           main_interest, stage, loss_reason, created_by, updated_by)
                VALUES (cast(? as uuid), 0, cast(? as uuid), ?, cast(? as uuid), cast(? as uuid),
                        ?, ?, ?, cast(? as uuid), cast(? as uuid))
                """,
                id.toString(),
                leadId.toString(),
                name,
                originId.toString(),
                responsibleId == null ? null : responsibleId.toString(),
                "Interesse " + name,
                stage,
                "LOST".equals(stage) ? "OTHER" : null,
                MANAGER.toString(),
                MANAGER.toString());
        return id;
    }

    private UUID insertLead(String name, UUID responsibleId) {
        UUID id = UUID.randomUUID();
        String phone = "1193000%04d".formatted(phoneSeq++);
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
