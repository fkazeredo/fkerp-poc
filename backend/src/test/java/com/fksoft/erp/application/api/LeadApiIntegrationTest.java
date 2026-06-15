package com.fksoft.erp.application.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fksoft.erp.AbstractIntegrationTest;
import com.fksoft.erp.domain.crm.LeadRepository;
import com.fksoft.erp.domain.crm.Origin;
import com.fksoft.erp.domain.crm.OriginRepository;
import com.fksoft.erp.domain.identity.AuthenticatedUser;
import com.fksoft.erp.infra.security.TokenService;
import com.jayway.jsonpath.JsonPath;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * End-to-end (MockMvc) of the lead-intake endpoint against a real Postgres: authentication, scope
 * enforcement, the full validation chain and the {@code {code,message,fields}} error contract.
 */
class LeadApiIntegrationTest extends AbstractIntegrationTest {

    // Boot 4 defaults to Jackson 3 (tools.jackson), so the Jackson 2 ObjectMapper is not a bean;
    // the test only needs it to build request JSON, so construct one directly.
    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private LeadRepository leads;

    @Autowired
    private OriginRepository origins;

    @Autowired
    private TokenService tokens;

    @BeforeEach
    void clean() {
        leads.deleteAll();
    }

    @Test
    void createsValidLeadAsNew() throws Exception {
        Map<String, Object> body = baseLead();
        body.put("initialNote", "Pediu retorno");
        postLead(body, login())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEW"));
        assertThat(leads.count()).isEqualTo(1);
    }

    @Test
    void createsLeadWithoutResponsible() throws Exception {
        postLead(baseLead(), login()).andExpect(status().isCreated());
        assertThat(leads.count()).isEqualTo(1);
    }

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        postLead(baseLead(), null).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenWithoutLeadCreateScope() throws Exception {
        String token = tokenWith("crm:reference:manage"); // authenticated, but missing crm:lead:create
        postLead(baseLead(), token).andExpect(status().isForbidden());
    }

    @Test
    void rejectsLeadWithoutNameWithErrorContract() throws Exception {
        Map<String, Object> body = baseLead();
        body.remove("name");
        postLead(body, login())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fields[*].field", hasItem("name")));
    }

    @Test
    void rejectsLeadWithoutOrigin() throws Exception {
        Map<String, Object> body = baseLead();
        body.remove("originId");
        postLead(body, login())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[*].field", hasItem("originId")));
    }

    @Test
    void rejectsLeadWithoutAnyContact() throws Exception {
        Map<String, Object> body = baseLead();
        body.remove("phone");
        postLead(body, login())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fields").isNotEmpty());
    }

    @Test
    void rejectsNonDigitPhone() throws Exception {
        Map<String, Object> body = baseLead();
        body.put("phone", "11-99999");
        postLead(body, login())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[*].field", hasItem("phone")));
    }

    @Test
    void rejectsInvalidEmail() throws Exception {
        Map<String, Object> body = baseLead();
        body.remove("phone");
        body.put("email", "not-an-email");
        postLead(body, login())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[*].field", hasItem("email")));
    }

    @Test
    void rejectsInactiveOrigin() throws Exception {
        Origin temp = Origin.create("TEMP_INACTIVE_LEAD", "Temp inativo", 90);
        temp.deactivate();
        origins.save(temp);

        Map<String, Object> body = new HashMap<>();
        body.put("name", "Maria Silva");
        body.put("phone", "11999999999");
        body.put("originId", temp.id().toString());

        postLead(body, login())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("lead.origin-not-available"));
    }

    @Test
    void doesNotCreateAnyLeadWhenRejected() throws Exception {
        Map<String, Object> body = baseLead();
        body.remove("name");
        postLead(body, login()).andExpect(status().isBadRequest());
        assertThat(leads.count()).isZero();
    }

    private Map<String, Object> baseLead() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Maria Silva");
        body.put("phone", "11999999999");
        body.put(
                "originId",
                origins.findByActiveTrueOrderBySortOrderAsc().get(0).id().toString());
        return body;
    }

    private ResultActions postLead(Map<String, Object> body, String token) throws Exception {
        MockHttpServletRequestBuilder request =
                post("/api/leads").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(request);
    }

    private String login() throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"comercial\",\"password\":\"comercial123\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private String tokenWith(String... scopes) {
        return tokens.issueAccessToken(new AuthenticatedUser(UUID.randomUUID(), "tester", Set.of(scopes)));
    }
}
