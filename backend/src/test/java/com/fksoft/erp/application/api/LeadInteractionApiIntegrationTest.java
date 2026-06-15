package com.fksoft.erp.application.api;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
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
import com.fksoft.erp.domain.crm.OriginRepository;
import com.fksoft.erp.domain.identity.AuthenticatedUser;
import com.fksoft.erp.infra.security.TokenService;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * End-to-end (MockMvc, real Postgres) of registering Lead interactions: the NEW → CONTACTED rule on
 * effective contact, failed attempts that keep NEW, validation, reference-data checks, the scheduled
 * next contact surfacing on the detail and list, append-only history, and auth/visibility.
 */
class LeadInteractionApiIntegrationTest extends AbstractIntegrationTest {

    private static final UUID SEED_USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_A = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private LeadRepository leads;

    @Autowired
    private OriginRepository origins;

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
    void registersAnEffectiveContactAndMovesTheLeadToContacted() throws Exception {
        String id = createLead(manager(), "Alpha", SEED_USER);

        mvc.perform(register(
                        id,
                        body(typeId("PHONE_CALL"), resultId("CONTACT_MADE"), "Conversamos", now(), null),
                        manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONTACTED"))
                .andExpect(jsonPath("$.interactions", hasSize(1)))
                .andExpect(jsonPath("$.interactions[0].type").value("Ligação"))
                .andExpect(jsonPath("$.interactions[0].result").value("Contato realizado"))
                .andExpect(jsonPath("$.interactions[0].content").value("Conversamos"))
                .andExpect(jsonPath("$.interactions[0].registeredBy").value("comercial"));
    }

    @Test
    void aNoAnswerAttemptKeepsTheLeadNew() throws Exception {
        String id = createLead(manager(), "NoAnswer", SEED_USER);

        mvc.perform(register(
                        id, body(typeId("PHONE_CALL"), resultId("NO_ANSWER"), "Não atendeu", now(), null), manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.interactions", hasSize(1)));
    }

    @Test
    void anInvalidContactAttemptKeepsTheLeadNew() throws Exception {
        String id = createLead(manager(), "Invalid", SEED_USER);

        mvc.perform(register(
                        id,
                        body(typeId("PHONE_CALL"), resultId("INVALID_CONTACT"), "Número errado", now(), null),
                        manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEW"));
    }

    @Test
    void rejectsAnInteractionWithoutDescription() throws Exception {
        String id = createLead(manager(), "NoDesc", SEED_USER);
        Map<String, Object> body = body(typeId("PHONE_CALL"), resultId("CONTACT_MADE"), "  ", now(), null);

        mvc.perform(register(id, body, manager()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fields[*].field", hasItem("description")));
    }

    @Test
    void rejectsAnInteractionWithoutTypeOrResult() throws Exception {
        String id = createLead(manager(), "NoType", SEED_USER);
        Map<String, Object> body = new HashMap<>();
        body.put("description", "sem tipo");
        body.put("occurredAt", now());

        mvc.perform(register(id, body, manager()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fields[*].field", hasItem("typeId")))
                .andExpect(jsonPath("$.fields[*].field", hasItem("resultId")));
    }

    @Test
    void rejectsAnInteractionDatedInTheFuture() throws Exception {
        String id = createLead(manager(), "Future", SEED_USER);
        String future = Instant.now().plus(Duration.ofDays(1)).toString();

        mvc.perform(register(
                        id, body(typeId("PHONE_CALL"), resultId("CONTACT_MADE"), "futuro", future, null), manager()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fields[*].field", hasItem("occurredAt")));
    }

    @Test
    void rejectsAnUnknownInteractionType() throws Exception {
        String id = createLead(manager(), "BadType", SEED_USER);

        mvc.perform(register(
                        id, body(UUID.randomUUID(), resultId("CONTACT_MADE"), "tipo inválido", now(), null), manager()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("interaction.type-not-available"));
    }

    @Test
    void rejectsAnUnknownInteractionResult() throws Exception {
        String id = createLead(manager(), "BadResult", SEED_USER);

        mvc.perform(register(
                        id,
                        body(typeId("PHONE_CALL"), UUID.randomUUID(), "resultado inválido", now(), null),
                        manager()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("interaction.result-not-available"));
    }

    @Test
    void schedulesNextContactShownInTheDetailAndOnTheList() throws Exception {
        String name = "Schedule " + UUID.randomUUID();
        String id = createLead(manager(), name, SEED_USER);
        String next = Instant.now().plus(Duration.ofDays(3)).toString();

        mvc.perform(register(
                        id,
                        body(typeId("PHONE_CALL"), resultId("INTERESTED"), "Quer proposta", now(), next),
                        manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextContactAt", notNullValue()))
                .andExpect(jsonPath("$.interactions[0].nextContactAt", notNullValue()));

        mvc.perform(get("/api/leads").param("q", name).header("Authorization", "Bearer " + manager()))
                .andExpect(jsonPath("$.content[0].nextContactAt", notNullValue()))
                .andExpect(jsonPath("$.content[0].lastInteractionType").value("Ligação"));
    }

    @Test
    void historyIsAppendOnlyAndIsNeverDeleted() throws Exception {
        String id = createLead(manager(), "History", SEED_USER);

        mvc.perform(register(
                        id, body(typeId("PHONE_CALL"), resultId("NO_ANSWER"), "tentativa 1", now(), null), manager()))
                .andExpect(status().isOk());
        mvc.perform(register(
                        id, body(typeId("WHATSAPP"), resultId("CONTACT_MADE"), "tentativa 2", now(), null), manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONTACTED"))
                .andExpect(jsonPath("$.interactions", hasSize(2)));

        mvc.perform(get("/api/leads/" + id).header("Authorization", "Bearer " + manager()))
                .andExpect(jsonPath("$.interactions", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    void returnsForbiddenWhenLeadNotVisibleToUser() throws Exception {
        String id = createLead(manager(), "Owned", SEED_USER);
        String regular = tokens.issueAccessToken(
                new AuthenticatedUser(USER_A, "ua", Set.of("crm:lead:read", "crm:lead:update")));

        mvc.perform(register(id, body(typeId("PHONE_CALL"), resultId("CONTACT_MADE"), "intruso", now(), null), regular))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("lead.access-denied"));
    }

    @Test
    void returnsNotFoundForUnknownLead() throws Exception {
        mvc.perform(register(
                        UUID.randomUUID().toString(),
                        body(typeId("PHONE_CALL"), resultId("CONTACT_MADE"), "fantasma", now(), null),
                        manager()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("lead.not-found"));
    }

    @Test
    void rejectsRegisteringWithoutUpdateScope() throws Exception {
        String id = createLead(manager(), "NoScope", SEED_USER);
        String readOnly = tokens.issueAccessToken(
                new AuthenticatedUser(SEED_USER, "comercial", Set.of("crm:lead:read", "crm:lead:read:all")));

        mvc.perform(register(
                        id, body(typeId("PHONE_CALL"), resultId("CONTACT_MADE"), "sem escopo", now(), null), readOnly))
                .andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder register(String id, Map<String, Object> body, String token) throws Exception {
        return post("/api/leads/" + id + "/interactions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body));
    }

    private static Map<String, Object> body(
            UUID typeId, UUID resultId, String description, String occurredAt, String nextContactAt) {
        Map<String, Object> body = new HashMap<>();
        body.put("typeId", typeId.toString());
        body.put("resultId", resultId.toString());
        body.put("description", description);
        body.put("occurredAt", occurredAt);
        if (nextContactAt != null) {
            body.put("nextContactAt", nextContactAt);
        }
        return body;
    }

    private static String now() {
        return Instant.now().toString();
    }

    private UUID typeId(String code) {
        return interactionTypes.findByCode(code).orElseThrow().id();
    }

    private UUID resultId(String code) {
        return interactionResults.findByCode(code).orElseThrow().id();
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
