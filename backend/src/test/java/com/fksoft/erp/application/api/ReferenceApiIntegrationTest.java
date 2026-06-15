package com.fksoft.erp.application.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fksoft.erp.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** End-to-end (MockMvc) CRUD of the Origin cadastro (read = authenticated, write = crm:reference:manage). */
class ReferenceApiIntegrationTest extends AbstractIntegrationTest {

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
    void createsNewOriginWithManageScope() throws Exception {
        mvc.perform(post("/api/crm/origins")
                        .header("Authorization", "Bearer " + login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"TIKTOK\",\"label\":\"TikTok\",\"sortOrder\":20}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("TIKTOK"))
                .andExpect(jsonPath("$.active").value(true));
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
