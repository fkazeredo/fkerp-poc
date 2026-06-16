package com.fksoft.erp.application.api;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fksoft.erp.AbstractIntegrationTest;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.repository.OriginRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * End-to-end (MockMvc, real Postgres) of the assignment authority: a manager assigns/reassigns to
 * anyone; a sales representative may only self-claim and cannot assign to another user or unassign.
 * Uses the two seed users (`comercial` = manager with {@code crm:lead:assign}; `vendedor` = rep).
 */
class LeadAssignmentApiIntegrationTest extends AbstractIntegrationTest {

    private static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID REP = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private LeadRepository leads;

    @Autowired
    private OriginRepository origins;

    @BeforeEach
    void clean() {
        leads.deleteAll();
    }

    @Test
    void managerAssignsUnassignedLeadToAnotherUser() throws Exception {
        String id = createLead(manager(), "Alpha", null);

        mvc.perform(reassign(id, REP, manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responsibleName").value("vendedor"))
                .andExpect(jsonPath("$.unassigned").value(false))
                .andExpect(jsonPath("$.assignments[0]").exists());

        mvc.perform(get("/api/leads").header("Authorization", "Bearer " + manager()))
                .andExpect(jsonPath("$.content[?(@.name=='Alpha')].responsibleName", hasItem("vendedor")));
    }

    @Test
    void managerCanReassign() throws Exception {
        String id = createLead(manager(), "Bravo", REP);

        mvc.perform(reassign(id, MANAGER, manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responsibleName").value("comercial"))
                .andExpect(jsonPath("$.assignments[0]").exists());
    }

    @Test
    void representativeCanSelfClaimAnUnassignedLead() throws Exception {
        String id = createLead(manager(), "Charlie", null);

        mvc.perform(reassign(id, REP, rep()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responsibleName").value("vendedor"));
    }

    @Test
    void representativeCannotAssignToAnotherUser() throws Exception {
        String id = createLead(manager(), "Delta", null);

        mvc.perform(reassign(id, MANAGER, rep()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("lead.assignment-not-allowed"));
    }

    @Test
    void representativeCannotUnassign() throws Exception {
        String id = createLead(manager(), "Echo", REP);

        mvc.perform(post("/api/leads/" + id + "/reassign")
                        .header("Authorization", "Bearer " + rep())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responsiblePersonId\":null}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("lead.assignment-not-allowed"));
    }

    @Test
    void cannotAssignToAnInactiveOrUnknownUser() throws Exception {
        String id = createLead(manager(), "Foxtrot", null);

        mvc.perform(reassign(id, UUID.randomUUID(), manager()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("lead.responsible-not-found"));
    }

    @Test
    void currentResponsibleAppearsInTheDetail() throws Exception {
        String id = createLead(manager(), "Golf", REP);

        mvc.perform(get("/api/leads/" + id).header("Authorization", "Bearer " + manager()))
                .andExpect(jsonPath("$.responsibleName").value("vendedor"))
                .andExpect(jsonPath("$.unassigned").value(false));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder reassign(
            String id, UUID toResponsible, String token) {
        return post("/api/leads/" + id + "/reassign")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"responsiblePersonId\":\"%s\"}".formatted(toResponsible));
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
        return login("comercial", "comercial123");
    }

    private String rep() throws Exception {
        return login("vendedor", "vendedor123");
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
