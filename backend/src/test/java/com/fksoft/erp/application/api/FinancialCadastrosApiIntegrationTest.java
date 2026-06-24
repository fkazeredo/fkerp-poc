package com.fksoft.erp.application.api;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * End-to-end (MockMvc, real Postgres) of the Financial cadastro (payment methods) under
 * {@code /api/financial/payment-methods} (Sprint 5 Slice 5): the endpoint is wired and seeded with the standard
 * payment methods, reading requires authentication and writing requires {@code reference:manage}. The generic
 * CRUD/duplicate contract is covered by {@link ReferenceApiIntegrationTest} (same base controller/service).
 */
class FinancialCadastrosApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TokenService tokens;

    @Test
    void listsSeededPaymentMethods() throws Exception {
        mvc.perform(get("/api/financial/payment-methods").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItem("PIX")))
                .andExpect(jsonPath("$[*].code", hasItem("CASH")))
                .andExpect(jsonPath("$[*].code", hasItem("OTHER")))
                .andExpect(jsonPath("$[*].label", hasItem("Pix")))
                .andExpect(jsonPath("$[*].label", hasItem("Dinheiro")));
    }

    @Test
    void createRequiresTheManageScope() throws Exception {
        String noScope = tokens.issueAccessToken(new AuthenticatedUser(UUID.randomUUID(), "viewer", Set.of()));
        mvc.perform(post("/api/financial/payment-methods")
                        .header("Authorization", "Bearer " + noScope)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"CHECK\",\"label\":\"Cheque\",\"sortOrder\":9}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsListWithoutToken() throws Exception {
        mvc.perform(get("/api/financial/payment-methods")).andExpect(status().isUnauthorized());
    }

    private String token() throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"financeiro\",\"password\":\"financeiro123\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
