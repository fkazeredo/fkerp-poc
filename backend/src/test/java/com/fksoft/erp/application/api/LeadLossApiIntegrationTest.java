package com.fksoft.erp.application.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
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
 * End-to-end (MockMvc, real Postgres) of marking a Lead as Lost: a reason is required and the note is
 * optional; loss records who/when; a Lost lead leaves the default operational list but is found via
 * the explicit LOST filter and cannot be qualified through the ordinary flow. Drives the real
 * {@code POST /lose} flow (not JDBC-inserted rows) and ties it to the list, qualify and detail.
 */
class LeadLossApiIntegrationTest extends AbstractIntegrationTest {

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
    void marksANewLeadLostWithAReasonAndNoNote() throws Exception {
        String id = createLead(manager(), "Alpha", SEED_USER);

        mvc.perform(lose(id, "{\"lossReasonId\":\"%s\"}".formatted(activeLossReasonId()), manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOST"))
                .andExpect(jsonPath("$.loss.reason", notNullValue()))
                .andExpect(jsonPath("$.loss.note", nullValue())) // note is optional
                .andExpect(jsonPath("$.loss.lostBy").value("comercial"))
                .andExpect(jsonPath("$.loss.lostAt", notNullValue()));
    }

    @Test
    void marksAContactedLeadLost() throws Exception {
        String id = createLead(manager(), "Bravo", SEED_USER);
        contact(id);

        mvc.perform(lose(id, "{\"lossReasonId\":\"%s\",\"note\":\"sumiu\"}".formatted(activeLossReasonId()), manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOST"))
                .andExpect(jsonPath("$.loss.note").value("sumiu"));
    }

    @Test
    void lostLeadLeavesTheDefaultListAndAppearsWhenFilteredByLost() throws Exception {
        String name = "Charlie " + UUID.randomUUID();
        String id = createLead(manager(), name, SEED_USER);
        mvc.perform(lose(id, "{\"lossReasonId\":\"%s\"}".formatted(activeLossReasonId()), manager()))
                .andExpect(status().isOk());

        mvc.perform(get("/api/leads").param("q", name).header("Authorization", "Bearer " + manager()))
                .andExpect(jsonPath("$.content[*].name", not(hasItem(name))));

        mvc.perform(get("/api/leads")
                        .param("q", name)
                        .param("status", "LOST")
                        .header("Authorization", "Bearer " + manager()))
                .andExpect(jsonPath("$.content[*].name", hasItem(name)));
    }

    @Test
    void lostLeadCannotBeQualifiedThroughTheOrdinaryFlow() throws Exception {
        String id = createLead(manager(), "Delta", SEED_USER);
        mvc.perform(lose(id, "{\"lossReasonId\":\"%s\"}".formatted(activeLossReasonId()), manager()))
                .andExpect(status().isOk());

        mvc.perform(post("/api/leads/" + id + "/qualify")
                        .header("Authorization", "Bearer " + manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mainInterest\":\"Pacote\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("lead.cannot-qualify"));
    }

    @Test
    void lossInformationAppearsInTheDetail() throws Exception {
        String id = createLead(manager(), "Echo", SEED_USER);
        mvc.perform(lose(
                        id, "{\"lossReasonId\":\"%s\",\"note\":\"motivo\"}".formatted(activeLossReasonId()), manager()))
                .andExpect(status().isOk());

        mvc.perform(get("/api/leads/" + id).header("Authorization", "Bearer " + manager()))
                .andExpect(jsonPath("$.status").value("LOST"))
                .andExpect(jsonPath("$.loss.reason", notNullValue()))
                .andExpect(jsonPath("$.loss.lostBy").value("comercial"))
                .andExpect(jsonPath("$.loss.note").value("motivo"));
    }

    @Test
    void rejectsLoseWithoutAReason() throws Exception {
        String id = createLead(manager(), "Foxtrot", SEED_USER);

        mvc.perform(lose(id, "{}", manager()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fields[*].field", hasItem("lossReasonId")));
    }

    @Test
    void rejectsLoseWithoutUpdateScope() throws Exception {
        String id = createLead(manager(), "Golf", SEED_USER);
        String readOnly = tokens.issueAccessToken(
                new AuthenticatedUser(SEED_USER, "comercial", Set.of("crm:lead:read", "crm:lead:read:all")));

        mvc.perform(lose(id, "{\"lossReasonId\":\"%s\"}".formatted(activeLossReasonId()), readOnly))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder lose(
            String id, String body, String token) {
        return post("/api/leads/" + id + "/lose")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private UUID activeLossReasonId() {
        return lossReasons.findByActiveTrueOrderBySortOrderAsc().get(0).id();
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
