package com.fksoft.erp.application.api;

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
 * End-to-end (MockMvc) CRUD of a reference cadastro against real Postgres: read = authenticated,
 * write = {@code reference:manage}; duplicate codes, the soft-delete lifecycle and the DB-backed
 * non-negative sort order are all covered.
 */
class ReferenceApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TokenService tokens;

    @Test
    void listsActiveOriginsForAuthenticatedUser() throws Exception {
        mvc.perform(get("/api/crm/origins").header("Authorization", "Bearer " + login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").exists());
    }

    @Test
    void rejectsListWithoutToken() throws Exception {
        mvc.perform(get("/api/crm/origins")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsWriteWithoutManageScope() throws Exception {
        String token = tokens.issueAccessToken(new AuthenticatedUser(UUID.randomUUID(), "viewer", Set.of()));
        mvc.perform(post("/api/crm/origins")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BLOCKED\",\"label\":\"Blocked\",\"sortOrder\":40}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createsNewOriginWithManageScope() throws Exception {
        mvc.perform(post("/api/crm/origins")
                        .header("Authorization", "Bearer " + login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"TIKTOK\",\"label\":\"TikTok\",\"sortOrder\":20}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("TIKTOK"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void rejectsDuplicateCodeWithConflict() throws Exception {
        mvc.perform(post("/api/crm/origins")
                        .header("Authorization", "Bearer " + login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"WEBSITE\",\"label\":\"Duplicado\",\"sortOrder\":50}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("reference.duplicate-code"));
    }

    @Test
    void rejectsBlankLabelWithErrorContract() throws Exception {
        mvc.perform(post("/api/crm/origins")
                        .header("Authorization", "Bearer " + login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"NOLABEL\",\"label\":\"\",\"sortOrder\":51}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));
    }

    @Test
    void rejectsNegativeSortOrderViaEntityValidation() throws Exception {
        // sortOrder has no DTO constraint; the negative value is caught by the entity's
        // @PositiveOrZero on flush (jakarta ConstraintViolationException), mapped to 400.
        mvc.perform(post("/api/crm/origins")
                        .header("Authorization", "Bearer " + login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"NEG_ORDER\",\"label\":\"Negativo\",\"sortOrder\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));
    }

    @Test
    void updatesDeactivatesAndFiltersInactive() throws Exception {
        String token = login();
        String created = mvc.perform(post("/api/crm/origins")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"PARTNER_PORTAL\",\"label\":\"Portal\",\"sortOrder\":60}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = JsonPath.read(created, "$.id");

        mvc.perform(put("/api/crm/origins/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Portal do parceiro\",\"sortOrder\":61,\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Portal do parceiro"));

        mvc.perform(delete("/api/crm/origins/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/crm/origins?includeInactive=false").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[?(@.code=='PARTNER_PORTAL')]").isEmpty());
        mvc.perform(get("/api/crm/origins?includeInactive=true").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[?(@.code=='PARTNER_PORTAL')]").isNotEmpty());
    }

    private String login() throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"comercial\",\"password\":\"comercial123\"}"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
