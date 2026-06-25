package com.fksoft.erp.application.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fksoft.erp.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end (MockMvc, real Postgres) of managing a Commission Rule (Commission Management, Sprint 6 Slice 1). A
 * commercial/financial manager (holding {@code commission:rule:manage}) creates, reads, updates and
 * activates/deactivates rules. The percentage is validated (greater than zero, at most 100, and not above the
 * configured safe limit unless explicitly allowed), the target user (when set) must exist, and only active rules
 * are listed by default. Creating a rule is configuration only — it creates no Commission record, Payment, payroll,
 * payable, tax or accounting data, and callers without the scope are 403/401.
 */
class CommissionRuleApiIntegrationTest extends AbstractIntegrationTest {

    // A known seeded, active user (the seller, 002) — used as a valid specific target.
    private static final UUID SELLER_USER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM commission_rules");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM commission_rules");
    }

    @Test
    void managerCreatesReadsAndListsACommissionRule() throws Exception {
        String mgr = login("comercial", "comercial123");
        String created = mvc.perform(
                        post("/api/commission/rules")
                                .header("Authorization", "Bearer " + mgr)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"name":"Padrão vendedores","percentage":5.00,"targetType":"SELLER",
                                 "startDate":"2026-01-01","notes":"comissão padrão"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = JsonPath.read(created, "$.id");

        mvc.perform(get("/api/commission/rules/" + id).header("Authorization", "Bearer " + mgr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Padrão vendedores"))
                .andExpect(jsonPath("$.percentage").value(5.00))
                .andExpect(jsonPath("$.targetType").value("SELLER"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.startDate").value("2026-01-01"))
                .andExpect(jsonPath("$.notes").value("comissão padrão"));

        // The list (default: active only) returns it; configuration only — no commission/payment fields.
        String list = mvc.perform(get("/api/commission/rules").header("Authorization", "Bearer " + mgr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(list.toLowerCase()).doesNotContain("commission_payment").doesNotContain("payroll");

        // No Commission/Payment table exists — only the rules table was created for this slice.
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM information_schema.tables WHERE table_name IN ('commissions','commission_payments')",
                        Integer.class))
                .isZero();
    }

    @Test
    void financeCanAlsoManageRules() throws Exception {
        mvc.perform(
                        post("/api/commission/rules")
                                .header("Authorization", "Bearer " + login("financeiro", "financeiro123"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"Finance\",\"percentage\":3.00,\"targetType\":\"COMMERCIAL_RESPONSIBLE\",\"startDate\":\"2026-01-01\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsABlankName() throws Exception {
        mvc.perform(
                        post("/api/commission/rules")
                                .header("Authorization", "Bearer " + login("comercial", "comercial123"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"  \",\"percentage\":5.00,\"targetType\":\"SELLER\",\"startDate\":\"2026-01-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAZeroOrNegativePercentage() throws Exception {
        String mgr = login("comercial", "comercial123");
        for (String pct : new String[] {"0", "-1"}) {
            mvc.perform(post("/api/commission/rules")
                            .header("Authorization", "Bearer " + mgr)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                    "{\"name\":\"R\",\"percentage\":%s,\"targetType\":\"SELLER\",\"startDate\":\"2026-01-01\"}"
                                            .formatted(pct)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void rejectsAPercentageAbove100() throws Exception {
        mvc.perform(
                        post("/api/commission/rules")
                                .header("Authorization", "Bearer " + login("comercial", "comercial123"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"R\",\"percentage\":100.01,\"targetType\":\"SELLER\",\"startDate\":\"2026-01-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAPercentageAboveTheSafeLimitWithoutTheExplicitFlag() throws Exception {
        mvc.perform(
                        post("/api/commission/rules")
                                .header("Authorization", "Bearer " + login("comercial", "comercial123"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"Alta\",\"percentage\":60.00,\"targetType\":\"SELLER\",\"startDate\":\"2026-01-01\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("commission.rule.percentage-above-limit"));
    }

    @Test
    void allowsAPercentageAboveTheSafeLimitWithTheExplicitFlag() throws Exception {
        mvc.perform(
                        post("/api/commission/rules")
                                .header("Authorization", "Bearer " + login("comercial", "comercial123"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"Alta\",\"percentage\":60.00,\"targetType\":\"SELLER\",\"startDate\":\"2026-01-01\",\"allowAboveLimit\":true}"))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsAnEndDateBeforeTheStart() throws Exception {
        mvc.perform(
                        post("/api/commission/rules")
                                .header("Authorization", "Bearer " + login("comercial", "comercial123"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"R\",\"percentage\":5.00,\"targetType\":\"SELLER\",\"startDate\":\"2026-06-01\",\"endDate\":\"2026-05-01\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("commission.rule.dates-invalid"));
    }

    @Test
    void rejectsAnUnknownTargetUser() throws Exception {
        mvc.perform(post("/api/commission/rules")
                        .header("Authorization", "Bearer " + login("comercial", "comercial123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"name\":\"R\",\"percentage\":5.00,\"targetType\":\"SELLER\",\"startDate\":\"2026-01-01\",\"targetUserId\":\"%s\"}"
                                        .formatted(UUID.randomUUID())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("commission.rule.target-user-not-found"));
    }

    @Test
    void createsAUserSpecificRuleWithAValidTargetUser() throws Exception {
        String mgr = login("comercial", "comercial123");
        String created = mvc.perform(post("/api/commission/rules")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"name\":\"Vendedor X\",\"percentage\":7.00,\"targetType\":\"SELLER\",\"startDate\":\"2026-01-01\",\"targetUserId\":\"%s\"}"
                                        .formatted(SELLER_USER)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = JsonPath.read(created, "$.id");
        mvc.perform(get("/api/commission/rules/" + id).header("Authorization", "Bearer " + mgr))
                .andExpect(jsonPath("$.targetUserId").value(SELLER_USER.toString()))
                .andExpect(jsonPath("$.targetUserName").value("vendedor"));
    }

    @Test
    void deactivateRemovesFromTheDefaultListAndActivateRestores() throws Exception {
        String mgr = login("comercial", "comercial123");
        String id = createRule(mgr, "R", "5.00");

        mvc.perform(post("/api/commission/rules/" + id + "/deactivate").header("Authorization", "Bearer " + mgr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        // Default list (active only) excludes it; includeInactive shows it.
        mvc.perform(get("/api/commission/rules").header("Authorization", "Bearer " + mgr))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/commission/rules?includeInactive=true").header("Authorization", "Bearer " + mgr))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].active").value(false));

        mvc.perform(post("/api/commission/rules/" + id + "/activate").header("Authorization", "Bearer " + mgr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        mvc.perform(get("/api/commission/rules").header("Authorization", "Bearer " + mgr))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void updateChangesTheEditableFields() throws Exception {
        String mgr = login("comercial", "comercial123");
        String id = createRule(mgr, "R", "5.00");
        mvc.perform(
                        put("/api/commission/rules/" + id)
                                .header("Authorization", "Bearer " + mgr)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"Atualizada\",\"percentage\":8.50,\"targetType\":\"SALES_REPRESENTATIVE\",\"startDate\":\"2026-02-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Atualizada"))
                .andExpect(jsonPath("$.percentage").value(8.50))
                .andExpect(jsonPath("$.targetType").value("SALES_REPRESENTATIVE"));
    }

    @Test
    void usersWithoutTheManageScopeAreForbidden() throws Exception {
        for (String[] user : new String[][] {
            {"vendedor", "vendedor123"}, {"representante", "representante123"}, {"diretor", "diretor123"}
        }) {
            String token = login(user[0], user[1]);
            mvc.perform(get("/api/commission/rules").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mvc.perform(
                            post("/api/commission/rules")
                                    .header("Authorization", "Bearer " + token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            "{\"name\":\"R\",\"percentage\":5.00,\"targetType\":\"SELLER\",\"startDate\":\"2026-01-01\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void rejectsUnauthenticated() throws Exception {
        mvc.perform(get("/api/commission/rules")).andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/api/commission/rules")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"R\",\"percentage\":5.00,\"targetType\":\"SELLER\",\"startDate\":\"2026-01-01\"}"))
                .andExpect(status().isUnauthorized());
    }

    private String createRule(String token, String name, String percentage) throws Exception {
        String created = mvc.perform(post("/api/commission/rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"name\":\"%s\",\"percentage\":%s,\"targetType\":\"SELLER\",\"startDate\":\"2026-01-01\"}"
                                        .formatted(name, percentage)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(created, "$.id");
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
