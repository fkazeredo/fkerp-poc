package com.fksoft.erp.application.api;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
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
 * End-to-end (MockMvc, real Postgres) of the Lead detail and its transitions: visibility/auth,
 * interaction history, qualify, mark-lost (with reason) and reassign — driven through the API.
 */
class LeadDetailApiIntegrationTest extends AbstractIntegrationTest {

    private static final UUID SEED_USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_A = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

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
    void opensDetailWithCoreDataAndInteractionHistory() throws Exception {
        String id = createLead(manager(), "Alpha", SEED_USER, "Primeira anotação");

        mvc.perform(get("/api/leads/" + id).header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alpha"))
                .andExpect(jsonPath("$.origin").value("Website"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.responsibleName").value("comercial"))
                .andExpect(jsonPath("$.interactions", hasSize(1)))
                .andExpect(jsonPath("$.interactions[0].content").value("Primeira anotação"))
                .andExpect(jsonPath("$.assignments", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void returnsNotFoundForUnknownLead() throws Exception {
        mvc.perform(get("/api/leads/" + UUID.randomUUID()).header("Authorization", "Bearer " + manager()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("lead.not-found"));
    }

    @Test
    void returnsForbiddenWhenLeadNotVisibleToUser() throws Exception {
        String id = createLead(manager(), "Owned", SEED_USER, null);
        String regular = tokens.issueAccessToken(new AuthenticatedUser(USER_A, "ua", Set.of("crm:lead:read")));

        mvc.perform(get("/api/leads/" + id).header("Authorization", "Bearer " + regular))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("lead.access-denied"));
    }

    @Test
    void rejectsDetailWithoutReadScope() throws Exception {
        String id = createLead(manager(), "Gamma", SEED_USER, null);
        String noRead = tokens.issueAccessToken(new AuthenticatedUser(USER_A, "ua", Set.of("crm:lead:create")));

        mvc.perform(get("/api/leads/" + id).header("Authorization", "Bearer " + noRead))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsDetailWhenUnauthenticated() throws Exception {
        mvc.perform(get("/api/leads/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void qualifiesAContactedLeadAndRecordsQualification() throws Exception {
        String id = createLead(manager(), "Qualify me", SEED_USER, null);
        contact(id);

        mvc.perform(post("/api/leads/" + id + "/qualify")
                        .header("Authorization", "Bearer " + manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mainInterest\":\"Pacote corporativo\",\"note\":\"bom perfil\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUALIFIED"))
                .andExpect(jsonPath("$.qualification.qualifiedBy").value("comercial"))
                .andExpect(jsonPath("$.qualification.mainInterest").value("Pacote corporativo"))
                .andExpect(jsonPath("$.qualification.note").value("bom perfil"));
    }

    @Test
    void marksALeadLostWithReasonAndRecordsLoss() throws Exception {
        String id = createLead(manager(), "Lose me", SEED_USER, null);
        UUID reasonId = activeLossReasonId();

        mvc.perform(post("/api/leads/" + id + "/lose")
                        .header("Authorization", "Bearer " + manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lossReasonId\":\"%s\",\"note\":\"sumiu\"}".formatted(reasonId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOST"))
                .andExpect(jsonPath("$.loss.reason", notNullValue()))
                .andExpect(jsonPath("$.loss.lostBy").value("comercial"));
    }

    @Test
    void rejectsLoseWithoutAReason() throws Exception {
        String id = createLead(manager(), "No reason", SEED_USER, null);

        mvc.perform(post("/api/leads/" + id + "/lose")
                        .header("Authorization", "Bearer " + manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));
    }

    @Test
    void rejectsLoseWithUnknownReason() throws Exception {
        String id = createLead(manager(), "Bad reason", SEED_USER, null);

        mvc.perform(post("/api/leads/" + id + "/lose")
                        .header("Authorization", "Bearer " + manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lossReasonId\":\"%s\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("lead.loss-reason-not-available"));
    }

    @Test
    void cannotQualifyOrLoseAnAlreadyLostLead() throws Exception {
        String id = createLead(manager(), "Already lost", SEED_USER, null);
        loseLead(id, activeLossReasonId());

        mvc.perform(post("/api/leads/" + id + "/qualify")
                        .header("Authorization", "Bearer " + manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mainInterest\":\"Pacote\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("lead.cannot-qualify"));
        mvc.perform(post("/api/leads/" + id + "/lose")
                        .header("Authorization", "Bearer " + manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lossReasonId\":\"%s\"}".formatted(activeLossReasonId())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("lead.cannot-mark-lost"));
    }

    @Test
    void reassignsResponsibleAndGrowsAssignmentHistory() throws Exception {
        String id = createLead(manager(), "Unassigned", null, null);

        mvc.perform(post("/api/leads/" + id + "/reassign")
                        .header("Authorization", "Bearer " + manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responsiblePersonId\":\"%s\"}".formatted(SEED_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responsibleName").value("comercial"))
                .andExpect(jsonPath("$.unassigned").value(false))
                .andExpect(jsonPath("$.assignments", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void rejectsTransitionWithoutUpdateScope() throws Exception {
        String id = createLead(manager(), "No update scope", SEED_USER, null);
        String readOnly = tokens.issueAccessToken(
                new AuthenticatedUser(SEED_USER, "comercial", Set.of("crm:lead:read", "crm:lead:read:all")));

        mvc.perform(post("/api/leads/" + id + "/qualify").header("Authorization", "Bearer " + readOnly))
                .andExpect(status().isForbidden());
    }

    private String createLead(String token, String name, UUID responsibleId, String initialNote) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("phone", "11999990000");
        body.put(
                "originId",
                origins.findByActiveTrueOrderBySortOrderAsc().get(0).id().toString());
        if (responsibleId != null) {
            body.put("responsiblePersonId", responsibleId.toString());
        }
        if (initialNote != null) {
            body.put("initialNote", initialNote);
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

    /** Moves a lead to CONTACTED by registering an effective interaction (so it can be qualified). */
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

    private void loseLead(String id, UUID reasonId) throws Exception {
        mvc.perform(post("/api/leads/" + id + "/lose")
                        .header("Authorization", "Bearer " + manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lossReasonId\":\"%s\"}".formatted(reasonId)))
                .andExpect(status().isOk());
    }

    private UUID activeLossReasonId() {
        return lossReasons.findByActiveTrueOrderBySortOrderAsc().get(0).id();
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
