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
import com.fksoft.erp.domain.crm.LossReasonRepository;
import com.fksoft.erp.domain.crm.OriginRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * End-to-end (MockMvc, real Postgres) of the operational pending-items worklist: every category is
 * surfaced with its reasons, non-pending Leads (qualified, lost, contacted-with-future-next,
 * new-with-interaction) are excluded, and visibility is respected (a representative sees only their
 * own pending; Finance has no access). Leads are set up through the real API so all invariants hold.
 */
class LeadPendingApiIntegrationTest extends AbstractIntegrationTest {

    private static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID REP = UUID.fromString("00000000-0000-0000-0000-000000000003");

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

    @BeforeEach
    void seed() throws Exception {
        leads.deleteAll();
        String mgr = login("comercial", "comercial123");

        createLead(mgr, "SemResp", null); // unassigned NEW, no interaction
        createLead(mgr, "RepNew", REP); // assigned to the rep, NEW, no interaction

        String stalled = createLead(mgr, "Stalled", MANAGER);
        interaction(stalled, "CONTACT_MADE", null); // CONTACTED, no next contact

        String overdue = createLead(mgr, "Overdue", MANAGER);
        interaction(
                overdue, "CONTACT_MADE", Instant.now().minus(Duration.ofDays(1)).toString());

        String future = createLead(mgr, "Future", MANAGER);
        interaction(
                future, "CONTACT_MADE", Instant.now().plus(Duration.ofDays(2)).toString());

        String qualified = createLead(mgr, "Qualified", MANAGER);
        interaction(qualified, "CONTACT_MADE", null);
        mvc.perform(post("/api/leads/" + qualified + "/qualify")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mainInterest\":\"Pacote\"}"))
                .andExpect(status().isOk());

        String lost = createLead(mgr, "Lost", MANAGER);
        mvc.perform(post("/api/leads/" + lost + "/lose")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lossReasonId\":\"%s\"}"
                                .formatted(lossReasons
                                        .findByActiveTrueOrderBySortOrderAsc()
                                        .get(0)
                                        .id())))
                .andExpect(status().isOk());

        String newWithInt = createLead(mgr, "NewWithInt", MANAGER);
        interaction(newWithInt, "NO_ANSWER", null); // stays NEW but now has an interaction
    }

    @Test
    void managerSeesEveryPendingCategoryAndExcludesNonPending() throws Exception {
        mvc.perform(get("/api/leads/pending").param("size", "50").header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", hasItem("SemResp")))
                .andExpect(jsonPath("$.content[*].name", hasItem("RepNew")))
                .andExpect(jsonPath("$.content[*].name", hasItem("Stalled")))
                .andExpect(jsonPath("$.content[*].name", hasItem("Overdue")))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("Future"))))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("Qualified"))))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("Lost"))))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("NewWithInt"))));
    }

    @Test
    void reasonsAreReportedPerLead() throws Exception {
        mvc.perform(get("/api/leads/pending").param("size", "50").header("Authorization", "Bearer " + manager()))
                .andExpect(jsonPath("$.content[?(@.name=='SemResp')].reasons[*]", hasItem("UNASSIGNED")))
                .andExpect(jsonPath("$.content[?(@.name=='SemResp')].reasons[*]", hasItem("NEW_WITHOUT_INTERACTION")))
                .andExpect(jsonPath("$.content[?(@.name=='Overdue')].reasons[*]", hasItem("OVERDUE_NEXT_CONTACT")))
                .andExpect(
                        jsonPath("$.content[?(@.name=='Stalled')].reasons[*]", hasItem("CONTACTED_WITHOUT_OUTCOME")));
    }

    @Test
    void representativeSeesOnlyOwnPending() throws Exception {
        String rep = login("representante", "representante123");
        mvc.perform(get("/api/leads/pending").param("size", "50").header("Authorization", "Bearer " + rep))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", hasItem("RepNew")))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("SemResp")))) // unassigned: invisible to a rep
                .andExpect(jsonPath("$.content[*].name", not(hasItem("Stalled"))))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("Overdue"))));
    }

    @Test
    void financeHasNoAccessToPending() throws Exception {
        String fin = login("financeiro", "financeiro123");
        mvc.perform(get("/api/leads/pending").header("Authorization", "Bearer " + fin))
                .andExpect(status().isForbidden());
    }

    private void interaction(String id, String resultCode, String nextContactAt) throws Exception {
        UUID typeId = interactionTypes.findByCode("PHONE_CALL").orElseThrow().id();
        UUID resultId = interactionResults.findByCode(resultCode).orElseThrow().id();
        Map<String, Object> body = new HashMap<>();
        body.put("typeId", typeId.toString());
        body.put("resultId", resultId.toString());
        body.put("description", "x");
        body.put("occurredAt", Instant.now().toString());
        if (nextContactAt != null) {
            body.put("nextContactAt", nextContactAt);
        }
        mvc.perform(post("/api/leads/" + id + "/interactions")
                        .header("Authorization", "Bearer " + manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    private String createLead(String token, String name, UUID responsibleId) throws Exception {
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
