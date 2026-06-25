package com.fksoft.erp.application.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Sprint 5 end-to-end validation: Financial Operations as one coherent flow through the real API — <b>not</b> as
 * isolated operations — starting from a Commercial Order whose booking is <b>actually confirmed through the Sprint
 * 3 → Sprint 4 flow</b> (proposal → accept → order → booking request → confirm), so the Sprint 4 → Sprint 5 handoff
 * (a CONFIRMED booking reflected onto the Order, and the Customer materialized from the Lead) is exercised for real.
 * Covers the four slice flows — the main full-payment flow (Receivable → Paid → Order ready for Commission), the
 * partial-payment flow (outstanding stays visible), the overdue flow (past-due → Overdue in the operational list,
 * no interest/fee) and the payment-reversal flow (Reversed, recalculated, reflected onto the Order) — asserting that
 * a Paid Receivable carries enough information for Sprint 6 Commission Management, that <b>no Commission, refund,
 * invoice or bank-reconciliation data</b> is created, and that the visibility rules hold throughout. No step depends
 * on data outside the system.
 */
class FinancialOperationsEndToEndIntegrationTest extends AbstractIntegrationTest {

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
    private int seq;

    @BeforeEach
    void seed() {
        wipe();
        originId = origins.findByActiveTrueOrderBySortOrderAsc().get(0).id();
        seq = 0;
    }

    @AfterEach
    void cleanup() {
        wipe();
    }

    // Derived financial/booking data first (FK-safe), then the commercial parents, leaving the shared container
    // clean for the other test classes.
    private void wipe() {
        jdbc.update("DELETE FROM receivables"); // cascades installments + payments
        jdbc.update("DELETE FROM booking_attempts");
        jdbc.update("DELETE FROM booking_items");
        jdbc.update("DELETE FROM booking_requests");
        jdbc.update("DELETE FROM commercial_order_items");
        orders.deleteAll();
        proposals.deleteAll();
        opportunities.deleteAll();
        jdbc.update("DELETE FROM customers");
        leads.deleteAll();
    }

    @Test
    void mainFlow_fullPayment_paidReceivable_orderReadyForCommission_noCommission() throws Exception {
        String fin = finance();
        // 1-3. From a Sprint-4 CONFIRMED-booking Order, finance creates a Receivable → OPEN, single installment.
        UUID order = confirmedBookingOrder();
        assertThat(orderField(order, fin, "$.bookingStatus")).isEqualTo("CONFIRMED");
        assertThat(orderField(order, fin, "$.financialStatus")).isNull();

        String receivable = createReceivable(fin, order, "2026-07-15");
        String openDetail = receivableDetail(fin, receivable);
        assertThat(JsonPath.<String>read(openDetail, "$.status")).isEqualTo("OPEN");
        assertThat(JsonPath.<List<Object>>read(openDetail, "$.installments")).hasSize(1);
        assertThat(JsonPath.<String>read(openDetail, "$.installments[0].status"))
                .isEqualTo("OPEN");
        assertThat(JsonPath.<List<Object>>read(openDetail, "$.payments")).isEmpty();
        assertThat(orderField(order, fin, "$.financialStatus")).isEqualTo("OPEN"); // reflected onto the Order

        // 4-8. Register the full payment → installment PAID, receivable PAID; the payment is recorded with its
        // method, date, amount and the registering user.
        String installment = JsonPath.read(openDetail, "$.installments[0].id");
        String paid = mvc.perform(
                        post("/api/receivables/%s/installments/%s/payments".formatted(receivable, installment))
                                .header("Authorization", "Bearer " + fin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"paymentMethodId\":\"%s\",\"amount\":500.00,\"paymentDate\":\"2026-06-01\",\"note\":\"pix recebido\"}"
                                                .formatted(paymentMethodId("PIX"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.installments[0].status").value("PAID"))
                .andExpect(jsonPath("$.amountPaid").value(500.00))
                .andExpect(jsonPath("$.outstandingAmount").value(0))
                .andExpect(jsonPath("$.payments[0].amount").value(500.00))
                .andExpect(jsonPath("$.payments[0].paymentDate").value("2026-06-01"))
                .andExpect(jsonPath("$.payments[0].paymentMethodLabel").value("Pix"))
                .andExpect(jsonPath("$.payments[0].registeredByName").value("financeiro"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 9-10. The Commercial Order reflects financial status PAID → identifiable as ready for Commission
        // Management (Sprint 6); the indicators surface it as a readiness count, not a Commission calculation.
        assertThat(orderField(order, fin, "$.financialStatus")).isEqualTo("PAID");
        assertThat(JsonPath.<Number>read(indicators(fin), "$.readyForCommission")
                        .intValue())
                .isEqualTo(1);

        // 11. No Commission/refund/invoice created, and the Paid Receivable carries enough for Sprint 6: the
        // commercial origin, the commercial total, the payer and the payment evidence.
        assertThat(JsonPath.<String>read(paid, "$.commercialOrderId")).isEqualTo(order.toString());
        assertThat(JsonPath.<Number>read(paid, "$.totalAmount").doubleValue()).isEqualTo(500.0);
        assertThat(JsonPath.<String>read(paid, "$.customerName")).isEqualTo("Lead Reserva");
        assertThat(JsonPath.<Map<String, Object>>read(paid, "$").keySet())
                .doesNotContain("commission", "refund", "invoice");
        assertThat(JsonPath.<Map<String, Object>>read(orderBody(order, fin), "$")
                        .keySet())
                .doesNotContain("commission", "refund", "invoice");
        // Exactly one Payment was recorded; the financial flow creates no Commission row (the commissions table
        // exists since Sprint 6 Slice 2, but Financial Operations never generates a commission).
        assertThat(jdbc.queryForObject("SELECT count(*) FROM receivable_payments", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commissions", Integer.class))
                .isZero();
    }

    @Test
    void partialFlow_partialPayment_partiallyPaid_outstandingStaysVisible_noCommission() throws Exception {
        String fin = finance();
        UUID order = confirmedBookingOrder();
        // A Receivable split into two installments (summing to the 500 commercial total).
        String created = mvc.perform(post("/api/receivables")
                        .header("Authorization", "Bearer " + fin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"commercialOrderId":"%s","dueDate":"2026-07-15","installments":[
                                  {"amount":200.00,"dueDate":"2026-07-15"},
                                  {"amount":300.00,"dueDate":"2026-08-15"}
                                ]}"""
                                        .formatted(order)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String receivable = JsonPath.read(created, "$.id");
        String firstInstallment = firstInstallmentId(fin, receivable);

        // A partial payment (100 of the 200 installment) → installment + receivable PARTIALLY_PAID; the outstanding
        // amount stays visible.
        mvc.perform(post("/api/receivables/%s/installments/%s/payments".formatted(receivable, firstInstallment))
                        .header("Authorization", "Bearer " + fin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodId\":\"%s\",\"amount\":100.00,\"paymentDate\":\"2026-06-01\"}"
                                .formatted(paymentMethodId("PIX"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.installments[0].status").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.amountPaid").value(100.00))
                .andExpect(jsonPath("$.outstandingAmount").value(400.00));

        // Reflected onto the Order as PARTIALLY_PAID — NOT ready for Commission, and no Commission created.
        assertThat(orderField(order, fin, "$.financialStatus")).isEqualTo("PARTIALLY_PAID");
        assertThat(JsonPath.<Number>read(indicators(fin), "$.readyForCommission")
                        .intValue())
                .isZero();
    }

    @Test
    void overdueFlow_pastDueReceivable_becomesOverdueInTheList_noInterestOrFee() throws Exception {
        String fin = finance();
        UUID order = confirmedBookingOrder();
        String receivable = createReceivable(fin, order, "2020-01-01"); // due in the past

        // Open with the full outstanding; not overdue until the daily check flags it.
        String before = receivableDetail(fin, receivable);
        assertThat(JsonPath.<String>read(before, "$.status")).isEqualTo("OPEN");
        assertThat(JsonPath.<Boolean>read(before, "$.overdue")).isFalse();
        assertThat(JsonPath.<Number>read(before, "$.outstandingAmount").doubleValue())
                .isEqualTo(500.0);

        overdueJob.markOverdue(LocalDate.now());

        // The receivable is identifiable as Overdue, the outstanding is unchanged (no interest/fee added), and it
        // appears in the operational list (the default view keeps OVERDUE visible).
        String after = receivableDetail(fin, receivable);
        assertThat(JsonPath.<String>read(after, "$.status")).isEqualTo("OVERDUE");
        assertThat(JsonPath.<Boolean>read(after, "$.overdue")).isTrue();
        assertThat(JsonPath.<Number>read(after, "$.outstandingAmount").doubleValue())
                .isEqualTo(500.0);
        assertThat(JsonPath.<Map<String, Object>>read(after, "$").keySet())
                .doesNotContain("interest", "fee", "notification", "commission");

        String list = mvc.perform(get("/api/receivables").header("Authorization", "Bearer " + fin))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(JsonPath.<List<String>>read(list, "$.content[*].id")).contains(receivable);
        assertThat(JsonPath.<List<Boolean>>read(list, "$.content[?(@.id=='" + receivable + "')].overdue"))
                .containsExactly(true);

        // Reflected onto the Order as a financial problem; the Order's own lifecycle is untouched.
        assertThat(orderField(order, fin, "$.financialStatus")).isEqualTo("OVERDUE");
        assertThat(orderField(order, fin, "$.status")).isEqualTo("PENDING_BOOKING");
    }

    @Test
    void reversalFlow_reversePayment_reversed_recalculated_orderUpdated_noRefund() throws Exception {
        String fin = finance();
        UUID order = confirmedBookingOrder();
        String receivable = createReceivable(fin, order, "2026-07-15");
        String installment = firstInstallmentId(fin, receivable);
        String paidBody = pay(fin, receivable, installment, "500.00"); // → PAID
        String paymentId = JsonPath.read(paidBody, "$.payments[0].id");
        assertThat(orderField(order, fin, "$.financialStatus")).isEqualTo("PAID");

        // Reverse the payment with a reason → the payment stays visible as Reversed; the paid amount and the
        // statuses are recalculated; the Order's financial status is updated.
        String reversed = mvc.perform(post("/api/receivables/%s/payments/%s/reversals".formatted(receivable, paymentId))
                        .header("Authorization", "Bearer " + fin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"lançamento incorreto\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.amountPaid").value(0))
                .andExpect(jsonPath("$.outstandingAmount").value(500.00))
                .andExpect(jsonPath("$.installments[0].status").value("OPEN"))
                .andExpect(jsonPath("$.payments.length()").value(1)) // kept in history (not deleted)
                .andExpect(jsonPath("$.payments[0].reversed").value(true))
                .andExpect(jsonPath("$.payments[0].reversalReason").value("lançamento incorreto"))
                .andExpect(jsonPath("$.payments[0].reversedByName").value("financeiro"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // The Order is no longer ready for Commission; no refund or Commission adjustment created.
        assertThat(orderField(order, fin, "$.financialStatus")).isEqualTo("OPEN");
        assertThat(JsonPath.<Number>read(indicators(fin), "$.readyForCommission")
                        .intValue())
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM receivable_payments", Integer.class))
                .isEqualTo(1); // still one row — reversed, not deleted (no refund row)
        assertThat(JsonPath.<Map<String, Object>>read(reversed, "$").keySet()).doesNotContain("refund", "commission");
    }

    @Test
    void visibilityHoldsThroughoutForUsersWithoutAFinancialReadTier() throws Exception {
        String fin = finance();
        UUID order = confirmedBookingOrder();
        String receivable = createReceivable(fin, order, "2026-07-15");

        // A seller and a representative have no financial read tier → 403 on the detail, the list and the indicators.
        for (String[] user : new String[][] {{"vendedor", "vendedor123"}, {"representante", "representante123"}}) {
            String token = login(user[0], user[1]);
            mvc.perform(get("/api/receivables/" + receivable).header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/receivables").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/receivables/indicators").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
    }

    // --- Sprint 5 financial helpers (driven through the real endpoints) ---

    private String createReceivable(String token, UUID order, String dueDate) throws Exception {
        String created = mvc.perform(post("/api/receivables")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\",\"dueDate\":\"%s\"}".formatted(order, dueDate)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(created, "$.id");
    }

    private String receivableDetail(String token, String receivable) throws Exception {
        return mvc.perform(get("/api/receivables/" + receivable).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String firstInstallmentId(String token, String receivable) throws Exception {
        return JsonPath.read(receivableDetail(token, receivable), "$.installments[0].id");
    }

    private String pay(String token, String receivable, String installment, String amount) throws Exception {
        return mvc.perform(post("/api/receivables/%s/installments/%s/payments".formatted(receivable, installment))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodId\":\"%s\",\"amount\":%s,\"paymentDate\":\"2026-06-01\"}"
                                .formatted(paymentMethodId("PIX"), amount)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String indicators(String token) throws Exception {
        return mvc.perform(get("/api/receivables/indicators").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String paymentMethodId(String code) {
        return jdbc.queryForObject("SELECT id FROM payment_methods WHERE code = ?", String.class, code);
    }

    // --- Sprint 4 booking confirmation: drive the Order's booking to CONFIRMED through the real API ---

    private UUID confirmedBookingOrder() throws Exception {
        UUID order = orderFromSprint3("TRAVEL_PACKAGE");
        String op = operator();
        UUID request = createBooking(order, op);
        String detail = bookingDetail(request, op);
        confirmTravelPackage(request, itemId(detail, "TRAVEL_PACKAGE"), op)
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        return order;
    }

    private UUID createBooking(UUID order, String token) throws Exception {
        String created = mvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\"}".formatted(order)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(JsonPath.read(created, "$.id"));
    }

    private ResultActions confirmTravelPackage(UUID request, UUID item, String token) throws Exception {
        return mvc.perform(
                        post("/api/bookings/" + request + "/items/" + item + "/confirm")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"externalSystem\":\"Amadeus\",\"externalLocator\":\"ABC123\",\"confirmedAt\":\"2026-06-10T10:00:00Z\"}"))
                .andExpect(status().isOk());
    }

    private String bookingDetail(UUID request, String token) throws Exception {
        return mvc.perform(get("/api/bookings/" + request).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private UUID itemId(String detail, String type) {
        return UUID.fromString(JsonPath.<List<String>>read(detail, "$.items[?(@.type=='" + type + "')].id")
                .get(0));
    }

    private String orderBody(UUID order, String token) throws Exception {
        return mvc.perform(get("/api/orders/" + order).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String orderField(UUID order, String token, String path) throws Exception {
        Object value = JsonPath.read(orderBody(order, token), path);
        return value == null ? null : value.toString();
    }

    // --- Sprint 3: build a PENDING_BOOKING Commercial Order through the real proposal → order API ---

    private UUID orderFromSprint3(String... itemTypes) throws Exception {
        String mgr = manager();
        UUID lead = insertLead();
        UUID opp = insertOpportunity(lead);
        UUID proposal = insertProposal(opp, lead);
        for (String type : itemTypes) {
            mvc.perform(post("/api/proposals/" + proposal + "/items")
                            .header("Authorization", "Bearer " + mgr)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"typeId\":\"%s\",\"description\":\"x\",\"quantity\":1,\"unitValue\":500.00}"
                                    .formatted(proposalItemTypeId(type))))
                    .andExpect(status().isOk());
        }
        mvc.perform(put("/api/proposals/" + proposal)
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"validUntil\":\"2026-12-31\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/proposals/" + proposal + "/submit").header("Authorization", "Bearer " + mgr))
                .andExpect(status().isOk());
        mvc.perform(post("/api/proposals/" + proposal + "/approve").header("Authorization", "Bearer " + mgr))
                .andExpect(status().isOk());
        mvc.perform(post("/api/proposals/" + proposal + "/send")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/proposals/" + proposal + "/accept")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        String created = mvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proposalId\":\"%s\"}".formatted(proposal)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(JsonPath.read(created, "$.id"));
    }

    private UUID insertProposal(UUID opportunityId, UUID leadId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO proposals (id, version, opportunity_id, lead_id, responsible_person_id, title,
                                       status, created_by, updated_by)
                VALUES (cast(? as uuid), 0, cast(? as uuid), cast(? as uuid), cast(? as uuid), 'Reserva', 'DRAFT',
                        cast(? as uuid), cast(? as uuid))
                """,
                id.toString(),
                opportunityId.toString(),
                leadId.toString(),
                MANAGER.toString(),
                MANAGER.toString(),
                MANAGER.toString());
        return id;
    }

    private UUID insertOpportunity(UUID leadId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO opportunities (id, version, lead_id, name, origin_id, responsible_person_id,
                                           main_interest, stage, loss_reason, created_by, updated_by)
                VALUES (cast(? as uuid), 0, cast(? as uuid), 'Opp Reserva', cast(? as uuid), cast(? as uuid),
                        'Pacote', ?, NULL, cast(? as uuid), cast(? as uuid))
                """,
                id.toString(),
                leadId.toString(),
                originId.toString(),
                MANAGER.toString(),
                "READY_FOR_PROPOSAL",
                MANAGER.toString(),
                MANAGER.toString());
        return id;
    }

    private UUID insertLead() {
        UUID id = UUID.randomUUID();
        String phone = "1190000%04d".formatted(seq++);
        jdbc.update(
                """
                INSERT INTO leads (id, name, phone, whatsapp, email, origin_id, status,
                                   responsible_person_id, loss_reason_id, created_at, updated_at,
                                   created_by, updated_by)
                VALUES (cast(? as uuid), 'Lead Reserva', ?, NULL, NULL, cast(? as uuid), 'NEW', cast(? as uuid),
                        NULL, now(), now(), cast(? as uuid), cast(? as uuid))
                """,
                id.toString(),
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

    private String operator() throws Exception {
        return login("operacoes", "operacoes123");
    }

    private String manager() throws Exception {
        return login("comercial", "comercial123");
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
