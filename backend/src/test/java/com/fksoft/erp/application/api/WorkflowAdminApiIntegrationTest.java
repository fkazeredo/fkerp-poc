package com.fksoft.erp.application.api;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fksoft.erp.AbstractIntegrationTest;
import com.fksoft.erp.domain.identity.AuthenticatedUser;
import com.fksoft.erp.infra.security.TokenService;
import com.jayway.jsonpath.JsonPath;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * End-to-end (MockMvc, real Postgres) of the workflow administration API under {@code /api/workflows}: it
 * requires the {@code workflow:manage} scope; it lists the workflows, exposes the editor detail
 * (states/transitions/attention rules) and the authoring catalog, and supports the attention-rule lifecycle
 * with the {@code system} lock (a system rule cannot be deleted) and the catalog validation.
 */
class WorkflowAdminApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TokenService tokens;

    @Test
    void listsTheConfigurableWorkflows() throws Exception {
        mvc.perform(get("/api/workflows").header("Authorization", "Bearer " + manage()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItem("opportunity")))
                .andExpect(jsonPath("$[*].code", hasItem("lead")))
                .andExpect(jsonPath("$[*].code", hasItem("booking_request")));
    }

    @Test
    void exposesTheEditorDetailWithStatesTransitionsAndAttentionRules() throws Exception {
        mvc.perform(get("/api/workflows/opportunity").header("Authorization", "Bearer " + manage()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.states[*].code", hasItem("DISCOVERY")))
                .andExpect(jsonPath("$.transitions[*].code").exists())
                .andExpect(jsonPath("$.attentionRules[*].code", hasItem("STUCK_IN_NEW")))
                .andExpect(jsonPath("$.attentionRules[?(@.code=='STUCK_IN_NEW')].system", hasItem(true)));
    }

    @Test
    void exposesTheAuthoringCatalog() throws Exception {
        mvc.perform(get("/api/workflows/catalog").header("Authorization", "Bearer " + manage()))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.attentionConditions.opportunity[*].conditionKey", hasItem("IN_STATE_LONGER_THAN")))
                .andExpect(jsonPath("$.guardKeys").exists())
                .andExpect(jsonPath("$.postFunctionKeys").exists());
    }

    @Test
    void createsUpdatesAndDeletesACustomAttentionRule() throws Exception {
        String token = manage();
        String created = mvc.perform(
                        post("/api/workflows/opportunity/attention-rules")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"conditionKey\":\"IN_STATE\",\"stateValue\":\"PRODUCT_FIT\",\"code\":\"STUCK_IN_PRODUCT_FIT\",\"label\":\"Parada em Product Fit\",\"sortOrder\":7}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = JsonPath.read(created, "$.id");

        mvc.perform(get("/api/workflows/opportunity").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.attentionRules[?(@.code=='STUCK_IN_PRODUCT_FIT')].system", hasItem(false)));

        mvc.perform(put("/api/workflows/attention-rules/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Renomeada\",\"sortOrder\":7,\"active\":false}"))
                .andExpect(status().isNoContent());

        mvc.perform(delete("/api/workflows/attention-rules/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void rejectsDeletingASystemAttentionRule() throws Exception {
        String token = manage();
        String detail = mvc.perform(get("/api/workflows/opportunity").header("Authorization", "Bearer " + token))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String systemRuleId = JsonPath.read(detail, "$.attentionRules[0].id");

        mvc.perform(delete("/api/workflows/attention-rules/" + systemRuleId).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("workflow.rule-system-protected"));
    }

    @Test
    void rejectsAnUnknownConditionKey() throws Exception {
        mvc.perform(post("/api/workflows/opportunity/attention-rules")
                        .header("Authorization", "Bearer " + manage())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conditionKey\":\"BOGUS\",\"code\":\"X\",\"label\":\"x\",\"sortOrder\":9}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("workflow.condition-unknown"));
    }

    @Test
    void requiresTheManageScope() throws Exception {
        String noScope = tokens.issueAccessToken(new AuthenticatedUser(UUID.randomUUID(), "viewer", Set.of()));
        mvc.perform(get("/api/workflows").header("Authorization", "Bearer " + noScope))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsWithoutToken() throws Exception {
        mvc.perform(get("/api/workflows")).andExpect(status().isUnauthorized());
    }

    private String manage() throws Exception {
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
