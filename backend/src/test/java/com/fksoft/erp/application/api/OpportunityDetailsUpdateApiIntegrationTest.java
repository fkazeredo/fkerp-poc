package com.fksoft.erp.application.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fksoft.erp.AbstractIntegrationTest;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.repository.OpportunityRepository;
import com.fksoft.erp.domain.crm.repository.OriginRepository;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end (MockMvc, real Postgres) of editing an Opportunity's commercial details (estimated value,
 * expected closing date, product type, commercial notes): updating and clearing them, the change
 * reflecting in the detail and the list, the main interest staying untouched, validation (negative value
 * / too-long product type), and the {@code crm:opportunity:update} scope + visibility checks. Editing
 * creates no Financial/Booking/Proposal/Commission data.
 */
class OpportunityDetailsUpdateApiIntegrationTest extends AbstractIntegrationTest {

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
        opportunities.deleteAll();
        leads.deleteAll();
        originId = origins.findByActiveTrueOrderBySortOrderAsc().get(0).id();
        phoneSeq = 0;
        managerOpp = insertOpportunity("Aurora", "NEW_OPPORTUNITY", MANAGER);
        insertOpportunity("Beta", "NEW_OPPORTUNITY", REPRESENTANTE);
        lostOpp = insertOpportunity("Gamma", "LOST", MANAGER);
    }

    @Test
    void updatesTheCommercialDetailsAndKeepsTheMainInterest() throws Exception {
        edit(
                        managerOpp,
                        """
                        {"estimatedValue":7500.00,"expectedCloseDate":"2026-09-30",
                         "productType":"Pacote de viagem","notes":"alta prioridade"}
                        """,
                        manager())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedValue").value(notNullValue()))
                .andExpect(jsonPath("$.expectedCloseDate").value("2026-09-30"))
                .andExpect(jsonPath("$.productType").value("Pacote de viagem"))
                .andExpect(jsonPath("$.notes").value("alta prioridade"))
                // The main interest comes from the Lead qualification and is not editable here.
                .andExpect(jsonPath("$.mainInterest").value("Interesse Aurora"));
    }

    @Test
    void clearsTheFieldsNotProvided() throws Exception {
        // The seed sets an estimated value; a PUT without it clears it (PUT carries the editable state).
        edit(managerOpp, "{\"productType\":\"Só produto\"}", manager())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productType").value("Só produto"))
                .andExpect(jsonPath("$.estimatedValue").isEmpty())
                .andExpect(jsonPath("$.expectedCloseDate").isEmpty())
                .andExpect(jsonPath("$.notes").isEmpty());
    }

    @Test
    void reflectsTheUpdateOnTheList() throws Exception {
        String token = manager();
        edit(managerOpp, "{\"estimatedValue\":4200.00,\"expectedCloseDate\":\"2026-10-15\"}", token)
                .andExpect(status().isOk());
        mvc.perform(get("/api/opportunities").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.name=='Aurora')].expectedCloseDate")
                        .value(hasItem("2026-10-15")));
    }

    @Test
    void allowsEditingALostOpportunity() throws Exception {
        edit(lostOpp, "{\"notes\":\"encerrada\"}", manager())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("encerrada"));
    }

    @Test
    void rejectsANegativeEstimatedValue() throws Exception {
        edit(managerOpp, "{\"estimatedValue\":-5}", manager()).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsATooLongProductType() throws Exception {
        edit(managerOpp, "{\"productType\":\"%s\"}".formatted("a".repeat(201)), manager())
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsEditingWithoutTheUpdateScope() throws Exception {
        // diretor (board) consults every Opportunity but holds no operation scope → 403 at the gate.
        edit(managerOpp, "{\"notes\":\"x\"}", login("diretor", "diretor123")).andExpect(status().isForbidden());
    }

    @Test
    void representativeCannotEditAnotherUsersOpportunity() throws Exception {
        edit(managerOpp, "{\"notes\":\"x\"}", login("representante", "representante123"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("opportunity.access-denied"));
    }

    @Test
    void returnsNotFoundForUnknownOpportunity() throws Exception {
        edit(UUID.randomUUID(), "{\"notes\":\"x\"}", manager())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("opportunity.not-found"));
    }

    private org.springframework.test.web.servlet.ResultActions edit(UUID id, String body, String token)
            throws Exception {
        return mvc.perform(put("/api/opportunities/" + id)
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
                new BigDecimal("1000.00"),
                "LOST".equals(stage) ? "OTHER" : null,
                MANAGER.toString(),
                MANAGER.toString());
        return id;
    }

    private UUID insertLead(String name, UUID responsibleId) {
        UUID id = UUID.randomUUID();
        String phone = "1194000%04d".formatted(phoneSeq++);
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
