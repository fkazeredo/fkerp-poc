package com.fksoft.erp.application.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fksoft.erp.AbstractIntegrationTest;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.repository.OpportunityRepository;
import com.fksoft.erp.domain.crm.repository.OriginRepository;
import com.fksoft.erp.domain.sales.repository.CommercialOrderRepository;
import com.fksoft.erp.domain.sales.repository.ProposalRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end (MockMvc, real Postgres) that the Commission status is reflected as a read-only summary onto the source
 * Commercial Order: generating reflects {@code EXPECTED}, paying the Receivable reflects {@code ELIGIBLE}, approving
 * reflects {@code APPROVED}, paying the commission reflects {@code PAID}, and a voided (Rejected/Cancelled) commission
 * reflects the distinct {@code ISSUE} summary. The reflection is Sales-owned: the Order's own lifecycle
 * ({@code status}) is never changed by Commission Management, and the Receivable row stays untouched.
 */
class OrderCommissionStatusReflectionApiIntegrationTest extends AbstractIntegrationTest {

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
    void theCommissionLifecycleIsReflectedOntoTheOrderUpToPaid() throws Exception {
        UUID order = closedOrder("Reflect");
        String manager = login("comercial", "comercial123");
        activeResponsibleRule(manager, "5");
        String fin = login("financeiro", "financeiro123");

        // No commission yet → no reflected commission status.
        assertThat(commissionStatusOf(order, manager)).isNull();

        // Generate → EXPECTED reflected; the Order's own lifecycle is untouched (Sales-owned).
        String commissionId = generate(manager, order);
        assertThat(commissionStatusOf(order, manager)).isEqualTo("EXPECTED");
        assertThat(lifecycleStatusOf(order, manager)).isEqualTo("PENDING_BOOKING");

        // Finance fully pays the Receivable → the commission becomes Eligible → ELIGIBLE reflected.
        fullyPayReceivableFor(fin, order);
        assertThat(commissionStatusOf(order, manager)).isEqualTo("ELIGIBLE");

        // Finance (not the beneficiary) approves → APPROVED reflected.
        mvc.perform(post("/api/commissions/" + commissionId + "/approve").header("Authorization", "Bearer " + fin))
                .andExpect(status().isOk());
        assertThat(commissionStatusOf(order, manager)).isEqualTo("APPROVED");

        // Finance registers the commission payment → PAID reflected; lifecycle still untouched.
        pay(fin, commissionId, "25.00");
        assertThat(commissionStatusOf(order, manager)).isEqualTo("PAID");
        assertThat(lifecycleStatusOf(order, manager)).isEqualTo("PENDING_BOOKING");

        // The Receivable row is untouched by the commission lifecycle (Financial keeps ownership).
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM receivables WHERE commercial_order_id = cast(? as uuid)",
                        String.class,
                        order.toString()))
                .isEqualTo("PAID");
    }

    @Test
    void aRejectedCommissionReflectsAnIssueOntoTheOrder() throws Exception {
        UUID order = closedOrder("Rejected");
        String manager = login("comercial", "comercial123");
        activeResponsibleRule(manager, "5");
        String fin = login("financeiro", "financeiro123");
        String commissionId = generate(manager, order);
        fullyPayReceivableFor(fin, order); // ELIGIBLE

        mvc.perform(post("/api/commissions/" + commissionId + "/reject")
                        .header("Authorization", "Bearer " + fin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonId\":\"%s\"}".formatted(resolutionReasonId("OTHER"))))
                .andExpect(status().isOk());

        // The Order shows the distinct ISSUE summary (commission voided, needs attention) — not null, not cancelled.
        assertThat(commissionStatusOf(order, manager)).isEqualTo("ISSUE");
        assertThat(lifecycleStatusOf(order, manager)).isEqualTo("PENDING_BOOKING");
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM receivables WHERE commercial_order_id = cast(? as uuid)",
                        String.class,
                        order.toString()))
                .isEqualTo("PAID");
    }

    @Test
    void aCancelledCommissionReflectsAnIssueOntoTheOrder() throws Exception {
        UUID order = closedOrder("Cancelled");
        String manager = login("comercial", "comercial123");
        activeResponsibleRule(manager, "5");
        String commissionId = generate(manager, order); // EXPECTED
        assertThat(commissionStatusOf(order, manager)).isEqualTo("EXPECTED");

        mvc.perform(post("/api/commissions/" + commissionId + "/cancel")
                        .header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonId\":\"%s\"}".formatted(resolutionReasonId("BUSINESS_EXCEPTION"))))
                .andExpect(status().isOk());

        assertThat(commissionStatusOf(order, manager)).isEqualTo("ISSUE");
        assertThat(lifecycleStatusOf(order, manager)).isEqualTo("PENDING_BOOKING");
    }

    @Test
    void theReflectionExposesNoPayrollOrPayableData() throws Exception {
        UUID order = closedOrder("NoLeak");
        String manager = login("comercial", "comercial123");
        activeResponsibleRule(manager, "5");
        generate(manager, order);

        String body = mvc.perform(get("/api/orders/" + order).header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).contains("\"commissionStatus\":\"EXPECTED\"");
        assertThat(body.toLowerCase()).doesNotContain("payroll").doesNotContain("payable");
    }

    private String commissionStatusOf(UUID order, String token) throws Exception {
        String body = mvc.perform(get("/api/orders/" + order).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.commissionStatus");
    }

    private String lifecycleStatusOf(UUID order, String token) throws Exception {
        String body = mvc.perform(get("/api/orders/" + order).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.status");
    }

    private String generate(String token, UUID order) throws Exception {
        return JsonPath.read(
                mvc.perform(post("/api/commissions")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"commercialOrderId\":\"%s\"}".formatted(order)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id");
    }

    private void pay(String token, String commissionId, String amount) throws Exception {
        mvc.perform(post("/api/commissions/" + commissionId + "/pay")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodId\":\"%s\",\"amount\":%s,\"paymentDate\":\"2026-06-20\"}"
                                .formatted(paymentMethodId("PIX"), amount)))
                .andExpect(status().isOk());
    }

    private void fullyPayReceivableFor(String fin, UUID order) throws Exception {
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
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.installments[0].id");
        mvc.perform(post("/api/receivables/%s/installments/%s/payments".formatted(receivable, installment))
                        .header("Authorization", "Bearer " + fin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodId\":\"%s\",\"amount\":500.00,\"paymentDate\":\"2026-06-01\"}"
                                .formatted(paymentMethodId("PIX"))))
                .andExpect(status().isOk());
    }

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

    private String resolutionReasonId(String code) {
        return jdbc.queryForObject("SELECT id FROM commission_resolution_reasons WHERE code = ?", String.class, code);
    }

    /** Seeds a confirmed-booking Order (with its customer + commercial chain), responsible = MANAGER, total 500. */
    private UUID closedOrder(String name) {
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
                        cast(? as uuid), cast(? as uuid), cast(? as uuid), 'PENDING_BOOKING', 'CONFIRMED', 500.00,
                        500.00, cast(? as uuid), cast(? as uuid))
                """,
                orderId.toString(),
                proposal.toString(),
                opportunity.toString(),
                lead.toString(),
                MANAGER.toString(),
                MANAGER.toString(),
                MANAGER.toString());
        jdbc.update(
                """
                INSERT INTO commercial_order_items (id, order_id, type, description, quantity, unit_value)
                VALUES (cast(? as uuid), cast(? as uuid), 'TRAVEL_PACKAGE', 'Pacote', 1, 500.00)
                """,
                UUID.randomUUID().toString(),
                orderId.toString());
        return orderId;
    }

    private void insertCustomer(UUID leadId, String name) {
        jdbc.update(
                """
                INSERT INTO customers (id, version, lead_id, name, status, created_by, updated_by)
                VALUES (cast(? as uuid), 0, cast(? as uuid), ?, 'ACTIVE', cast(? as uuid), cast(? as uuid))
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
