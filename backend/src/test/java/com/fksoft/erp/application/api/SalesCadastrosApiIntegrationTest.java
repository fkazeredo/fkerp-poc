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
 * End-to-end (MockMvc, real Postgres) of the Sales cadastros (proposal-rejection / customer-rejection
 * reasons, sending channels) under {@code /api/sales/...}: each endpoint is wired and seeded with the former
 * enum values, read requires authentication and writing requires {@code reference:manage}. The generic
 * CRUD/duplicate contract is covered by {@link ReferenceApiIntegrationTest} (same base controller/service).
 */
class SalesCadastrosApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TokenService tokens;

    @Test
    void listsSeededProposalRejectionReasons() throws Exception {
        mvc.perform(get("/api/sales/proposal-rejection-reasons").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItem("PRICE_TOO_HIGH")))
                .andExpect(jsonPath("$[*].label", hasItem("Preço muito alto")));
    }

    @Test
    void listsSeededCustomerRejectionReasons() throws Exception {
        mvc.perform(get("/api/sales/customer-rejection-reasons").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItem("CHOSE_COMPETITOR")));
    }

    @Test
    void listsSeededSendingChannels() throws Exception {
        mvc.perform(get("/api/sales/sending-channels").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItem("EMAIL")))
                .andExpect(jsonPath("$[*].label", hasItem("E-mail")));
    }

    @Test
    void createRequiresTheManageScope() throws Exception {
        String noScope = tokens.issueAccessToken(new AuthenticatedUser(UUID.randomUUID(), "viewer", Set.of()));
        mvc.perform(post("/api/sales/sending-channels")
                        .header("Authorization", "Bearer " + noScope)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"SMS\",\"label\":\"SMS\",\"sortOrder\":9}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsListWithoutToken() throws Exception {
        mvc.perform(get("/api/sales/sending-channels")).andExpect(status().isUnauthorized());
    }

    private String token() throws Exception {
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
