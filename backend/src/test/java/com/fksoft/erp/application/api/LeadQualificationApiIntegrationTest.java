package com.fksoft.erp.application.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fksoft.erp.AbstractIntegrationTest;
import com.fksoft.erp.domain.crm.InteractionResultRepository;
import com.fksoft.erp.domain.crm.InteractionTypeRepository;
import com.fksoft.erp.domain.crm.LeadRepository;
import com.fksoft.erp.domain.crm.LossReasonRepository;
import com.fksoft.erp.domain.crm.OriginRepository;
import com.fksoft.erp.domain.identity.AuthenticatedUser;
import com.fksoft.erp.infra.security.TokenService;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * End-to-end (MockMvc, real Postgres) of qualifying a Lead: requires a CONTACTED lead with a
 * responsible person and a main interest; records who/when; keeps the lead visible/traceable; and
 * creates no Opportunity/Customer (no such tables/endpoints exist in Sprint 1, so this holds
 * structurally — nothing to create).
 */
class LeadQualificationApiIntegrationTest extends AbstractIntegrationTest {

    private static final UUID SEED_USER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private LeadRepository leads;

    @Autowired
    private OriginRepository origins;

    @Autowired
    private LossReasonRepository lossReasons;

    @Autowired
    private InteractionTypeRepository interactionTypes;

    @Autowired
    private InteractionResultRepository interactionResults;

    @Autowired
    private TokenService tokens;

    @BeforeEach
    void clean() {
        leads.deleteAll();
    }

    @Test
    void qualifiesAContactedAssignedLeadAndStaysVisible() throws Exception {
        String name = "Qualify " + UUID.randomUUID();
        String id = createLead(manager(), name, SEED_USER);
        contact(id);

        mvc.perform(qualify(id, "{\"mainInterest\":\"Pacote corporativo\",\"note\":\"bom perfil\"}", manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUALIFIED"))
                .andExpect(jsonPath("$.qualification.mainInterest").value("Pacote corporativo"))
                .andExpect(jsonPath("$.qualification.note").value("bom perfil"))
                .andExpect(jsonPath("$.qualification.qualifiedBy").value("comercial"))
                .andExpect(jsonPath("$.qualification.qualifiedAt", notNullValue()));

        // Remains visible and traceable on the operational list.
        mvc.perform(get("/api/leads").param("q", name).header("Authorization", "Bearer " + manager()))
                .andExpect(jsonPath("$.content[?(@.name=='" + name + "')].status", hasItem("QUALIFIED")));
    }

    @Test
    void rejectsQualifyingANewLead() throws Exception {
        String id = createLead(manager(), "Still new", SEED_USER);

        mvc.perform(qualify(id, "{\"mainInterest\":\"Pacote\"}", manager()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("lead.cannot-qualify"));
    }

    @Test
    void rejectsQualifyingALostLead() throws Exception {
        String id = createLead(manager(), "Lost one", SEED_USER);
        UUID reasonId = lossReasons.findByActiveTrueOrderBySortOrderAsc().get(0).id();
        mvc.perform(post("/api/leads/" + id + "/lose")
                        .header("Authorization", "Bearer " + manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lossReasonId\":\"%s\"}".formatted(reasonId)))
                .andExpect(status().isOk());

        mvc.perform(qualify(id, "{\"mainInterest\":\"Pacote\"}", manager()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("lead.cannot-qualify"));
    }

    @Test
    void rejectsQualifyingAContactedLeadWithoutResponsible() throws Exception {
        String id = createLead(manager(), "No owner", null);
        contact(id);

        mvc.perform(qualify(id, "{\"mainInterest\":\"Pacote\"}", manager()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("lead.qualification-requires-responsible"));
    }

    @Test
    void rejectsQualifyingWithoutMainInterest() throws Exception {
        String id = createLead(manager(), "No interest", SEED_USER);
        contact(id);

        mvc.perform(qualify(id, "{\"note\":\"sem interesse informado\"}", manager()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fields[*].field", hasItem("mainInterest")));
    }

    @Test
    void rejectsQualifyingWithoutUpdateScope() throws Exception {
        String id = createLead(manager(), "No scope", SEED_USER);
        contact(id);
        String readOnly = tokens.issueAccessToken(
                new AuthenticatedUser(SEED_USER, "comercial", Set.of("crm:lead:read", "crm:lead:read:all")));

        mvc.perform(qualify(id, "{\"mainInterest\":\"Pacote\"}", readOnly)).andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder qualify(
            String id, String body, String token) {
        return post("/api/leads/" + id + "/qualify")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private void contact(String id) throws Exception {
        UUID typeId = interactionTypes.findByCode("PHONE_CALL").orElseThrow().id();
        UUID resultId =
                interactionResults.findByCode("CONTACT_MADE").orElseThrow().id();
        mvc.perform(post("/api/leads/" + id + "/interactions")
                        .header("Authorization", "Bearer " + manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"typeId\":\"%s\",\"resultId\":\"%s\",\"description\":\"contato\",\"occurredAt\":\"%s\"}"
                                        .formatted(typeId, resultId, Instant.now())))
                .andExpect(status().isOk());
    }

    private String createLead(String token, String name, UUID responsibleId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("phone", "11999990000");
        body.put(
                "originId",
                origins.findByActiveTrueOrderBySortOrderAsc().get(0).id().toString());
        if (responsibleId != null) {
            body.put("responsiblePersonId", responsibleId.toString());
        }
        String response = mvc.perform(post("/api/leads")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private String manager() throws Exception {
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
