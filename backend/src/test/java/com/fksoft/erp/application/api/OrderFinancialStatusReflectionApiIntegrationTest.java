package com.fksoft.erp.application.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fksoft.erp.AbstractIntegrationTest;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.repository.OpportunityRepository;
import com.fksoft.erp.domain.crm.repository.OriginRepository;
import com.fksoft.erp.domain.financial.service.ReceivableOverdueJob;
import com.fksoft.erp.domain.sales.repository.CommercialOrderRepository;
import com.fksoft.erp.domain.sales.repository.ProposalRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end (MockMvc, real Postgres) that the Receivable's financial status is reflected onto the source
 * Commercial Order: creating the Receivable reflects {@code OPEN}, a partial payment reflects
 * {@code PARTIALLY_PAID} (not treated as paid), settling it reflects {@code PAID} (the Order is identifiable as
 * ready for Commission Management), and the daily overdue check reflects {@code OVERDUE} (a financial problem).
 * The reflection is Sales-owned: the Order's own lifecycle ({@code status}) is never changed by Financial, and no
 * Commission data is created.
 */
class OrderFinancialStatusReflectionApiIntegrationTest extends AbstractIntegrationTest {

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
    private ReceivableOverdueJob overdueJob;

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
        jdbc.update("DELETE FROM receivables");
        jdbc.update("DELETE FROM commercial_order_items");
        orders.deleteAll();
        proposals.deleteAll();
        opportunities.deleteAll();
        jdbc.update("DELETE FROM customers");
        leads.deleteAll();
    }

    @Test
    void creatingAReceivableReflectsOpenAndPaymentsDriveTheReflection() throws Exception {
        String fin = finance();
        UUID order = confirmedOrder("Reflect");

        // No Receivable yet → no reflected financial status.
        assertThat(financialStatusOf(order, fin)).isNull();

        String receivableId = createReceivable(fin, order, "2026-07-15");
        assertThat(financialStatusOf(order, fin)).isEqualTo("OPEN");
        // The Order's own lifecycle is untouched (Sales-owned).
        assertThat(lifecycleStatusOf(order, fin)).isEqualTo("PENDING_BOOKING");

        String installment = firstInstallmentId(fin, receivableId);
        // Partial payment → reflected PARTIALLY_PAID (not treated as paid).
        pay(fin, receivableId, installment, "200.00");
        assertThat(financialStatusOf(order, fin)).isEqualTo("PARTIALLY_PAID");

        // Settling the balance → reflected PAID (ready for Commission Management), lifecycle still untouched.
        pay(fin, receivableId, installment, "300.00");
        assertThat(financialStatusOf(order, fin)).isEqualTo("PAID");
        assertThat(lifecycleStatusOf(order, fin)).isEqualTo("PENDING_BOOKING");
    }

    @Test
    void theDailyOverdueCheckReflectsOverdueOntoTheOrder() throws Exception {
        String fin = finance();
        UUID order = confirmedOrder("Overdue");
        createReceivable(fin, order, "2020-01-01"); // due in the past
        assertThat(financialStatusOf(order, fin)).isEqualTo("OPEN");

        // The daily overdue check flags the past-due receivable and reflects OVERDUE onto the Order.
        overdueJob.markOverdue(LocalDate.now());

        assertThat(financialStatusOf(order, fin)).isEqualTo("OVERDUE");
        // The Order is identifiable as a financial problem, but is NOT cancelled (Financial takes no ownership).
        assertThat(lifecycleStatusOf(order, fin)).isEqualTo("PENDING_BOOKING");
    }

    private void pay(String token, String receivableId, String installmentId, String amount) throws Exception {
        mvc.perform(post("/api/receivables/%s/installments/%s/payments".formatted(receivableId, installmentId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodId\":\"%s\",\"amount\":%s,\"paymentDate\":\"2026-06-01\"}"
                                .formatted(paymentMethodId("PIX"), amount)))
                .andExpect(status().isOk());
    }

    private String createReceivable(String token, UUID order, String dueDate) throws Exception {
        String created = mvc.perform(post("/api/receivables")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\",\"dueDate\":\"%s\"}".formatted(order, dueDate)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(created, "$.id");
    }

    private String firstInstallmentId(String token, String receivableId) throws Exception {
        String detail = mvc.perform(get("/api/receivables/" + receivableId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(detail, "$.installments[0].id");
    }

    private String paymentMethodId(String code) {
        return jdbc.queryForObject("SELECT id FROM payment_methods WHERE code = ?", String.class, code);
    }

    private String financialStatusOf(UUID order, String token) throws Exception {
        String body = mvc.perform(get("/api/orders/" + order).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.financialStatus");
    }

    private String lifecycleStatusOf(UUID order, String token) throws Exception {
        String body = mvc.perform(get("/api/orders/" + order).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.status");
    }

    /** Seeds a confirmed-booking Order (with its customer + commercial chain) so a Receivable can be created. */
    private UUID confirmedOrder(String name) {
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

    private String finance() throws Exception {
        return login("financeiro", "financeiro123");
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
