package com.fksoft.erp.application.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * End-to-end (MockMvc, real Postgres) of profile-based Lead visibility, using the seed users:
 * {@code comercial} (manager: all + operate), {@code vendedor} (seller: own + pool),
 * {@code representante} (own only), {@code diretor} (consult all, no operate) and {@code financeiro}
 * (no access). Visibility applies to lists, details, filters and actions.
 */
class LeadVisibilityApiIntegrationTest extends AbstractIntegrationTest {

    private static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID REPRESENTANTE = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private LeadRepository leads;

    @Autowired
    private OriginRepository origins;

    @Autowired
    private InteractionTypeRepository interactionTypes;

    @Autowired
    private InteractionResultRepository interactionResults;

    private String mineId; // owned by representante
    private String othersId; // owned by manager
    private String pooledId; // unassigned

    @BeforeEach
    void seed() throws Exception {
        leads.deleteAll();
        String mgr = login("comercial", "comercial123");
        mineId = createLead(mgr, "Minha rep", REPRESENTANTE);
        othersId = createLead(mgr, "Do gerente", MANAGER);
        pooledId = createLead(mgr, "No pool", null);
    }

    @Test
    void representativeSeesOnlyOwnLeads() throws Exception {
        String rep = login("representante", "representante123");
        mvc.perform(get("/api/leads").header("Authorization", "Bearer " + rep))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", hasItem("Minha rep")))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("Do gerente"))))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("No pool"))));
    }

    @Test
    void representativeCannotOpenOthersOrThePool() throws Exception {
        String rep = login("representante", "representante123");
        mvc.perform(get("/api/leads/" + mineId).header("Authorization", "Bearer " + rep))
                .andExpect(status().isOk());
        mvc.perform(get("/api/leads/" + othersId).header("Authorization", "Bearer " + rep))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("lead.access-denied"));
        mvc.perform(get("/api/leads/" + pooledId).header("Authorization", "Bearer " + rep))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("lead.access-denied"));
    }

    @Test
    void representativeCannotOperateOthersButOperatesOwn() throws Exception {
        String rep = login("representante", "representante123");
        // Own lead: can register an interaction.
        mvc.perform(interaction(mineId, rep)).andExpect(status().isOk());
        // Another's lead: blocked by visibility even though the rep holds crm:lead:update.
        mvc.perform(interaction(othersId, rep))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("lead.access-denied"));
        mvc.perform(post("/api/leads/" + othersId + "/reassign")
                        .header("Authorization", "Bearer " + rep)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responsiblePersonId\":\"%s\"}".formatted(REPRESENTANTE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("lead.access-denied"));
    }

    @Test
    void filtersDoNotBypassVisibility() throws Exception {
        String rep = login("representante", "representante123");
        // Filtering by the manager's id must not surface the manager's lead.
        mvc.perform(get("/api/leads").param("responsible", MANAGER.toString()).header("Authorization", "Bearer " + rep))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("Do gerente"))));
        // Filtering the unassigned pool must not surface pooled leads for an own-only rep.
        mvc.perform(get("/api/leads").param("responsible", "unassigned").header("Authorization", "Bearer " + rep))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("No pool"))));
    }

    @Test
    void managerSeesEveryLead() throws Exception {
        String mgr = login("comercial", "comercial123");
        mvc.perform(get("/api/leads").header("Authorization", "Bearer " + mgr))
                .andExpect(jsonPath("$.content[*].name", hasItem("Minha rep")))
                .andExpect(jsonPath("$.content[*].name", hasItem("Do gerente")))
                .andExpect(jsonPath("$.content[*].name", hasItem("No pool")));
    }

    @Test
    void directorConsultsEverythingButCannotOperate() throws Exception {
        String dir = login("diretor", "diretor123");
        // Consultation: list + detail of any lead.
        mvc.perform(get("/api/leads").header("Authorization", "Bearer " + dir))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", hasItem("Do gerente")));
        mvc.perform(get("/api/leads/" + othersId).header("Authorization", "Bearer " + dir))
                .andExpect(status().isOk());
        // No operate scopes → every action is forbidden.
        mvc.perform(createLeadRequest(dir, "Tentativa", null)).andExpect(status().isForbidden());
        mvc.perform(interaction(mineId, dir)).andExpect(status().isForbidden());
        mvc.perform(post("/api/leads/" + mineId + "/reassign")
                        .header("Authorization", "Bearer " + dir)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responsiblePersonId\":null}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/leads/" + mineId + "/qualify")
                        .header("Authorization", "Bearer " + dir)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mainInterest\":\"X\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void financeHasNoAccessToLeads() throws Exception {
        String fin = login("financeiro", "financeiro123");
        mvc.perform(get("/api/leads").header("Authorization", "Bearer " + fin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/leads/" + mineId).header("Authorization", "Bearer " + fin))
                .andExpect(status().isForbidden());
        mvc.perform(createLeadRequest(fin, "Nope", null)).andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder interaction(String id, String token) {
        UUID typeId = interactionTypes.findByCode("PHONE_CALL").orElseThrow().id();
        UUID resultId =
                interactionResults.findByCode("CONTACT_MADE").orElseThrow().id();
        return post("/api/leads/" + id + "/interactions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"typeId\":\"%s\",\"resultId\":\"%s\",\"description\":\"x\",\"occurredAt\":\"%s\"}"
                        .formatted(typeId, resultId, Instant.now()));
    }

    private MockHttpServletRequestBuilder createLeadRequest(String token, String name, UUID responsibleId)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put(
                "phone",
                "119%08d"
                        .formatted(
                                java.util.concurrent.ThreadLocalRandom.current().nextInt(100_000_000)));
        body.put(
                "originId",
                origins.findByActiveTrueOrderBySortOrderAsc().get(0).id().toString());
        if (responsibleId != null) {
            body.put("responsiblePersonId", responsibleId.toString());
        }
        return post("/api/leads")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body));
    }

    private String createLead(String token, String name, UUID responsibleId) throws Exception {
        String response = mvc.perform(createLeadRequest(token, name, responsibleId))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.id");
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
