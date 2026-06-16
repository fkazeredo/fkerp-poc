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
import com.fksoft.erp.domain.crm.Origin;
import com.fksoft.erp.domain.crm.OriginRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * End-to-end (MockMvc, real Postgres) of the minimum Lead indicators: status/origin/responsible
 * counts (Lost included), waiting-for-first-contact, the period filter, and visibility scoping (a
 * representative's numbers cover only their own Leads; Finance has no access). Leads are set up
 * through the real API so all invariants hold.
 */
class LeadIndicatorsApiIntegrationTest extends AbstractIntegrationTest {

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

    private UUID website;
    private UUID instagram;

    @BeforeEach
    void seed() throws Exception {
        leads.deleteAll();
        List<Origin> active = origins.findByActiveTrueOrderBySortOrderAsc();
        website = active.get(0).id();
        instagram = active.get(1).id();
        String mgr = login("comercial", "comercial123");

        createLead(mgr, "U1", null, website); // NEW unassigned, no interaction (waiting)
        createLead(mgr, "M1", MANAGER, website); // NEW manager, no interaction (waiting)

        String m2 = createLead(mgr, "M2", MANAGER, instagram); // NEW manager, has interaction (not waiting)
        interaction(m2, "NO_ANSWER", null);

        String m3 = createLead(mgr, "M3", MANAGER, website); // CONTACTED manager
        interaction(m3, "CONTACT_MADE", null);

        String r1 = createLead(mgr, "R1", REP, instagram); // QUALIFIED rep
        interaction(r1, "CONTACT_MADE", null);
        mvc.perform(post("/api/leads/" + r1 + "/qualify")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mainInterest\":\"Pacote\"}"))
                .andExpect(status().isOk());

        String m4 = createLead(mgr, "M4", MANAGER, website); // LOST manager
        mvc.perform(post("/api/leads/" + m4 + "/lose")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lossReasonId\":\"%s\"}"
                                .formatted(lossReasons
                                        .findByActiveTrueOrderBySortOrderAsc()
                                        .get(0)
                                        .id())))
                .andExpect(status().isOk());

        createLead(mgr, "R2", REP, website); // NEW rep, no interaction (waiting)
    }

    @Test
    void managerSeesGlobalIndicatorsIncludingLost() throws Exception {
        mvc.perform(get("/api/leads/indicators").header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(7))
                .andExpect(jsonPath("$.newLeads").value(4))
                .andExpect(jsonPath("$.contacted").value(1))
                .andExpect(jsonPath("$.qualified").value(1))
                .andExpect(jsonPath("$.lost").value(1))
                .andExpect(jsonPath("$.waitingFirstContact").value(3))
                .andExpect(jsonPath("$.byOrigin[?(@.origin=='Website')].count", hasItem(5)))
                .andExpect(jsonPath("$.byOrigin[?(@.origin=='Instagram')].count", hasItem(2)))
                .andExpect(jsonPath("$.byResponsible[?(@.responsibleName=='comercial')].count", hasItem(4)))
                .andExpect(jsonPath("$.byResponsible[?(@.responsibleName=='representante')].count", hasItem(2)));
    }

    @Test
    void unassignedShowsAsANullResponsibleBucketForTheManager() throws Exception {
        mvc.perform(get("/api/leads/indicators").header("Authorization", "Bearer " + manager()))
                .andExpect(jsonPath("$.byResponsible[?(@.responsibleName == null)].count", hasItem(1)));
    }

    @Test
    void thePeriodFilterNarrowsTheCounts() throws Exception {
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        String tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1).toString();
        mvc.perform(get("/api/leads/indicators")
                        .param("createdFrom", today)
                        .header("Authorization", "Bearer " + manager()))
                .andExpect(jsonPath("$.total").value(7));
        mvc.perform(get("/api/leads/indicators")
                        .param("createdFrom", tomorrow)
                        .header("Authorization", "Bearer " + manager()))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void representativeIndicatorsAreScopedToTheirOwnLeads() throws Exception {
        String rep = login("representante", "representante123");
        mvc.perform(get("/api/leads/indicators").header("Authorization", "Bearer " + rep))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2)) // only R1 (qualified) + R2 (new)
                .andExpect(jsonPath("$.newLeads").value(1))
                .andExpect(jsonPath("$.qualified").value(1))
                .andExpect(jsonPath("$.contacted").value(0))
                .andExpect(jsonPath("$.lost").value(0))
                .andExpect(jsonPath("$.waitingFirstContact").value(1))
                .andExpect(jsonPath("$.byResponsible[?(@.responsibleName=='representante')].count", hasItem(2)))
                .andExpect(jsonPath("$.byResponsible[*].responsibleName", not(hasItem("comercial"))));
    }

    @Test
    void financeHasNoAccessToIndicators() throws Exception {
        String fin = login("financeiro", "financeiro123");
        mvc.perform(get("/api/leads/indicators").header("Authorization", "Bearer " + fin))
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

    private String createLead(String token, String name, UUID responsibleId, UUID originId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("phone", "11999990000");
        body.put("originId", originId.toString());
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
