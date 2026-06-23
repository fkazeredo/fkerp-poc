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
 * End-to-end (MockMvc, real Postgres) of the Booking cadastros (attempt type/result, failure reason) under
 * {@code /api/booking/...}: each endpoint is wired and seeded with the former enum values, read requires
 * authentication and writing requires {@code reference:manage}. The generic CRUD/duplicate contract is
 * covered by {@link ReferenceApiIntegrationTest} (same base controller/service).
 */
class BookingCadastrosApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TokenService tokens;

    @Test
    void listsSeededAttemptTypes() throws Exception {
        mvc.perform(get("/api/booking/booking-attempt-types").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItem("INTERNAL_VERIFICATION")))
                .andExpect(jsonPath("$[*].label", hasItem("Verificação interna")));
    }

    @Test
    void listsSeededAttemptResults() throws Exception {
        mvc.perform(get("/api/booking/booking-attempt-results").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItem("STARTED")));
    }

    @Test
    void listsSeededFailureReasons() throws Exception {
        mvc.perform(get("/api/booking/booking-failure-reasons").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItem("NO_AVAILABILITY")))
                .andExpect(jsonPath("$[*].label", hasItem("Sem disponibilidade")));
    }

    @Test
    void createRequiresTheManageScope() throws Exception {
        String noScope = tokens.issueAccessToken(new AuthenticatedUser(UUID.randomUUID(), "viewer", Set.of()));
        mvc.perform(post("/api/booking/booking-failure-reasons")
                        .header("Authorization", "Bearer " + noScope)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"WEATHER\",\"label\":\"Clima\",\"sortOrder\":9}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsListWithoutToken() throws Exception {
        mvc.perform(get("/api/booking/booking-attempt-types")).andExpect(status().isUnauthorized());
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
