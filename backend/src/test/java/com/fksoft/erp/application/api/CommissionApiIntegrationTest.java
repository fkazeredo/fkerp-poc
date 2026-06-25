package com.fksoft.erp.application.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fksoft.erp.AbstractIntegrationTest;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.repository.OpportunityRepository;
import com.fksoft.erp.domain.crm.repository.OriginRepository;
import com.fksoft.erp.domain.sales.repository.CommercialOrderRepository;
import com.fksoft.erp.domain.sales.repository.ProposalRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end (MockMvc, real Postgres) of generating an Expected Commission from a commercially-closed Commercial
 * Order: an authorized manager (holding {@code commission:create}) generates the forecast commission, which preserves
 * the commercial origin (Order/Proposal/Opportunity/Lead) + beneficiary + applied rule, calculates the amount from
 * the commercial total (or the received amount when available) and starts EXPECTED. Only a closed order with a
 * responsible, a positive total and an applicable active rule can originate one (else 422), at most one active
 * Commission per order (else 409), an unknown order is 404, and callers without the commission scope are 403/401.
 * Generating creates no Commission Payment, Accounts Payable, payroll, tax or accounting data.
 */
class CommissionApiIntegrationTest extends AbstractIntegrationTest {

    private static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private CommercialOrderRepository orders;

    @Autowired
    private ProposalRepository proposals;

    @Autowired
    private OpportunityRepository opportunities;

    @Autowired
    private LeadRepository leads;

    @Autowired
    private OriginRepository origins;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID originId;
    private int phoneSeq;

    @BeforeEach
    void seed() {
        wipe();
        originId = origins.findByActiveTrueOrderBySortOrderAsc().get(0).id();
        phoneSeq = 0;
    }

    @AfterEach
    void cleanup() {
        wipe();
    }

    // Commissions/receivables are derived data of the commercial parents; delete them (and the rules) first
    // (FK-safe) so the shared container is left clean and no stale active rule leaks into the next test.
    private void wipe() {
        jdbc.update("DELETE FROM commissions");
        jdbc.update("DELETE FROM commission_rules");
        jdbc.update("DELETE FROM receivables");
        jdbc.update("DELETE FROM commercial_order_items");
        orders.deleteAll();
        proposals.deleteAll();
        opportunities.deleteAll();
        jdbc.update("DELETE FROM customers");
        leads.deleteAll();
    }

    @Test
    void managerGeneratesAnExpectedCommissionForecastFromAClosedOrder() throws Exception {
        UUID order = closedOrder("Forecast", "PENDING_BOOKING", MANAGER, "500.00", "CONFIRMED");
        String manager = login("comercial", "comercial123");
        activeResponsibleRule(manager, "5");

        String created = mvc.perform(post("/api/commissions")
                        .header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\"}".formatted(order)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String commissionId = JsonPath.read(created, "$.id");

        String body = mvc.perform(get("/api/commissions/" + commissionId).header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPECTED"))
                .andExpect(jsonPath("$.commercialOrderId").value(order.toString()))
                .andExpect(jsonPath("$.basisType").value("COMMERCIAL_AMOUNT"))
                .andExpect(jsonPath("$.baseAmount").value(500.00))
                .andExpect(jsonPath("$.rulePercentage").value(5.00))
                .andExpect(jsonPath("$.amount").value(25.00))
                .andExpect(jsonPath("$.beneficiaryUserId").value(MANAGER.toString()))
                .andExpect(jsonPath("$.orderNumber").isNumber())
                .andExpect(jsonPath("$.ruleName").value("Comissão padrão"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // The contract exposes commission + commercial-origin data only — never Commission Payment, Payables,
        // payroll, tax or accounting data.
        Map<String, Object> detail = JsonPath.read(body, "$");
        assertThat(detail.keySet())
                .containsExactlyInAnyOrder(
                        "id",
                        "commercialOrderId",
                        "orderNumber",
                        "proposalId",
                        "opportunityId",
                        "leadId",
                        "beneficiaryUserId",
                        "beneficiaryName",
                        "ruleId",
                        "ruleName",
                        "rulePercentage",
                        "basisType",
                        "baseAmount",
                        "amount",
                        "status",
                        "createdAt");
        assertThat(body.toLowerCase()).doesNotContain("payable").doesNotContain("payroll");

        // Exactly one Commission row; no Commission-payment table exists in this slice.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commissions", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void generatesFromTheReceivedAmountWhenTheReceivableIsAlreadyPartiallyPaid() throws Exception {
        UUID order = closedOrder("Received", "PENDING_BOOKING", MANAGER, "500.00", "CONFIRMED");
        String fin = login("financeiro", "financeiro123");

        // Finance creates the Receivable and registers a partial payment of 200 (of 500).
        String receivable = JsonPath.read(
                mvc.perform(post("/api/receivables")
                                .header("Authorization", "Bearer " + fin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"commercialOrderId\":\"%s\",\"dueDate\":\"2026-07-15\"}".formatted(order)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id");
        String installment = JsonPath.read(
                mvc.perform(get("/api/receivables/" + receivable).header("Authorization", "Bearer " + fin))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.installments[0].id");
        mvc.perform(post("/api/receivables/%s/installments/%s/payments".formatted(receivable, installment))
                        .header("Authorization", "Bearer " + fin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodId\":\"%s\",\"amount\":200.00,\"paymentDate\":\"2026-06-01\"}"
                                .formatted(paymentMethodId("PIX"))))
                .andExpect(status().isOk());

        activeResponsibleRule(fin, "5");
        String commissionId = JsonPath.read(
                mvc.perform(post("/api/commissions")
                                .header("Authorization", "Bearer " + fin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"commercialOrderId\":\"%s\"}".formatted(order)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id");

        // The commission was calculated from the received amount (200), not the commercial total (500).
        mvc.perform(get("/api/commissions/" + commissionId).header("Authorization", "Bearer " + fin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basisType").value("RECEIVED_AMOUNT"))
                .andExpect(jsonPath("$.baseAmount").value(200.00))
                .andExpect(jsonPath("$.amount").value(10.00));
    }

    @Test
    void cannotGenerateWhenNoActiveRuleApplies() throws Exception {
        UUID order = closedOrder("NoRule", "PENDING_BOOKING", MANAGER, "500.00", "CONFIRMED");

        mvc.perform(post("/api/commissions")
                        .header("Authorization", "Bearer " + login("comercial", "comercial123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\"}".formatted(order)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("commission.no-applicable-rule"));
    }

    @Test
    void cannotGenerateFromACancelledOrder() throws Exception {
        UUID order = closedOrder("Cancelled", "CANCELLED", MANAGER, "500.00", "CONFIRMED");
        String manager = login("comercial", "comercial123");
        activeResponsibleRule(manager, "5");

        mvc.perform(post("/api/commissions")
                        .header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\"}".formatted(order)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("commission.order-not-closed"));
    }

    @Test
    void cannotGenerateFromAnOrderWithoutAResponsible() throws Exception {
        UUID order = closedOrder("NoResp", "PENDING_BOOKING", null, "500.00", "CONFIRMED");

        mvc.perform(post("/api/commissions")
                        .header("Authorization", "Bearer " + login("comercial", "comercial123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\"}".formatted(order)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("commission.order-no-responsible"));
    }

    @Test
    void cannotGenerateFromAnOrderWithoutAPositiveAmount() throws Exception {
        UUID order = closedOrder("ZeroAmount", "PENDING_BOOKING", MANAGER, "0.00", "CONFIRMED");

        mvc.perform(post("/api/commissions")
                        .header("Authorization", "Bearer " + login("comercial", "comercial123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\"}".formatted(order)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("commission.order-no-amount"));
    }

    @Test
    void cannotGenerateADuplicateActiveCommission() throws Exception {
        UUID order = closedOrder("Dup", "PENDING_BOOKING", MANAGER, "500.00", "CONFIRMED");
        String manager = login("comercial", "comercial123");
        activeResponsibleRule(manager, "5");

        String first = JsonPath.read(
                mvc.perform(post("/api/commissions")
                                .header("Authorization", "Bearer " + manager)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"commercialOrderId\":\"%s\"}".formatted(order)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id");

        mvc.perform(post("/api/commissions")
                        .header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\"}".formatted(order)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("commission.already-exists"))
                .andExpect(jsonPath("$.fields[0].field").value("commissionId"))
                .andExpect(jsonPath("$.fields[0].message").value(first));
    }

    @Test
    void returnsNotFoundForAnUnknownSourceOrder() throws Exception {
        mvc.perform(post("/api/commissions")
                        .header("Authorization", "Bearer " + login("comercial", "comercial123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("commission.order-not-found"));
    }

    @Test
    void returnsNotFoundForAnUnknownCommissionOnDetail() throws Exception {
        mvc.perform(get("/api/commissions/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + login("comercial", "comercial123")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("commission.not-found"));
    }

    @Test
    void rejectsAMissingOrderId() throws Exception {
        mvc.perform(post("/api/commissions")
                        .header("Authorization", "Bearer " + login("comercial", "comercial123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnUnauthenticatedGenerate() throws Exception {
        mvc.perform(post("/api/commissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aSellerWithoutTheCommissionScopeIsForbidden() throws Exception {
        UUID order = closedOrder("Forbidden", "PENDING_BOOKING", MANAGER, "500.00", "CONFIRMED");
        String seller = login("vendedor", "vendedor123");

        mvc.perform(post("/api/commissions")
                        .header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\"}".formatted(order)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/commissions/" + UUID.randomUUID()).header("Authorization", "Bearer " + seller))
                .andExpect(status().isForbidden());
    }

    @Test
    void theDirectorConsultsButCannotGenerate() throws Exception {
        UUID order = closedOrder("Consult", "PENDING_BOOKING", MANAGER, "500.00", "CONFIRMED");
        String manager = login("comercial", "comercial123");
        activeResponsibleRule(manager, "5");
        String commissionId = JsonPath.read(
                mvc.perform(post("/api/commissions")
                                .header("Authorization", "Bearer " + manager)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"commercialOrderId\":\"%s\"}".formatted(order)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id");

        // The Board/Director holds commission:read (consultation) but not commission:create.
        String director = login("diretor", "diretor123");
        mvc.perform(get("/api/commissions/" + commissionId).header("Authorization", "Bearer " + director))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPECTED"));
        mvc.perform(post("/api/commissions")
                        .header("Authorization", "Bearer " + director)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\"}".formatted(order)))
                .andExpect(status().isForbidden());
    }

    // Creates an active, in-window COMMERCIAL_RESPONSIBLE rule (so it matches any order's commercial responsible).
    private void activeResponsibleRule(String token, String percentage) throws Exception {
        mvc.perform(post("/api/commission/rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"name":"Comissão padrão","percentage":%s,"targetType":"COMMERCIAL_RESPONSIBLE",
                                 "startDate":"2026-01-01"}"""
                                        .formatted(percentage)))
                .andExpect(status().isCreated());
    }

    private String paymentMethodId(String code) {
        return jdbc.queryForObject("SELECT id FROM payment_methods WHERE code = ?", String.class, code);
    }

    /** Seeds an Order (with its commercial chain) with the given status, responsible, total and booking status. */
    private UUID closedOrder(String name, String status, UUID responsible, String total, String bookingStatus) {
        UUID lead = insertLead(name);
        insertCustomer(lead, "Lead " + name);
        UUID opportunity = insertOpportunity(name, lead);
        UUID proposal = insertProposal(name, opportunity, lead);
        UUID orderId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO commercial_orders (id, version, number, proposal_id, opportunity_id, lead_id,
                                               responsible_person_id, status, booking_status, subtotal, total,
                                               created_by, updated_by)
                VALUES (cast(? as uuid), 0, nextval('commercial_order_number_seq'), cast(? as uuid),
                        cast(? as uuid), cast(? as uuid), cast(? as uuid), ?, ?, cast(? as numeric),
                        cast(? as numeric), cast(? as uuid), cast(? as uuid))
                """,
                orderId.toString(),
                proposal.toString(),
                opportunity.toString(),
                lead.toString(),
                responsible == null ? null : responsible.toString(),
                status,
                bookingStatus,
                total,
                total,
                MANAGER.toString(),
                MANAGER.toString());
        jdbc.update(
                """
                INSERT INTO commercial_order_items (id, order_id, type, description, quantity, unit_value)
                VALUES (cast(? as uuid), cast(? as uuid), 'TRAVEL_PACKAGE', 'Pacote', 1, cast(? as numeric))
                """,
                UUID.randomUUID().toString(),
                orderId.toString(),
                total);
        return orderId;
    }

    // Mirrors the Customer materialized at Order creation in production (the jdbc-seeded Order skips the event).
    private void insertCustomer(UUID leadId, String name) {
        jdbc.update(
                """
                INSERT INTO customers (id, version, lead_id, name, active, created_by, updated_by)
                VALUES (cast(? as uuid), 0, cast(? as uuid), ?, TRUE, cast(? as uuid), cast(? as uuid))
                """,
                UUID.randomUUID().toString(),
                leadId.toString(),
                name,
                MANAGER.toString(),
                MANAGER.toString());
    }

    private UUID insertProposal(String name, UUID opportunityId, UUID leadId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO proposals (id, version, opportunity_id, lead_id, responsible_person_id, title,
                                       status, subtotal, total, created_by, updated_by)
                VALUES (cast(? as uuid), 0, cast(? as uuid), cast(? as uuid), cast(? as uuid), ?,
                        'ACCEPTED', 500.00, 500.00, cast(? as uuid), cast(? as uuid))
                """,
                id.toString(),
                opportunityId.toString(),
                leadId.toString(),
                MANAGER.toString(),
                "Proposta " + name,
                MANAGER.toString(),
                MANAGER.toString());
        return id;
    }

    private UUID insertOpportunity(String name, UUID leadId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO opportunities (id, version, lead_id, name, origin_id, responsible_person_id,
                                           main_interest, stage, loss_reason, created_by, updated_by)
                VALUES (cast(? as uuid), 0, cast(? as uuid), ?, cast(? as uuid), cast(? as uuid),
                        ?, 'WON', NULL, cast(? as uuid), cast(? as uuid))
                """,
                id.toString(),
                leadId.toString(),
                name,
                originId.toString(),
                MANAGER.toString(),
                "Pacote " + name,
                MANAGER.toString(),
                MANAGER.toString());
        return id;
    }

    private UUID insertLead(String name) {
        UUID id = UUID.randomUUID();
        String phone = "1190000%04d".formatted(phoneSeq++);
        jdbc.update(
                """
                INSERT INTO leads (id, name, phone, whatsapp, email, origin_id, status,
                                   responsible_person_id, loss_reason_id, created_at, updated_at,
                                   created_by, updated_by)
                VALUES (cast(? as uuid), ?, ?, NULL, NULL, cast(? as uuid), 'NEW', cast(? as uuid),
                        NULL, now(), now(), cast(? as uuid), cast(? as uuid))
                """,
                id.toString(),
                "Lead " + name,
                phone,
                originId.toString(),
                MANAGER.toString(),
                MANAGER.toString(),
                MANAGER.toString());
        return id;
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
