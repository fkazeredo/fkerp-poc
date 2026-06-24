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
import com.fksoft.erp.domain.financial.service.ReceivableOverdueJob;
import com.fksoft.erp.domain.sales.repository.CommercialOrderRepository;
import com.fksoft.erp.domain.sales.repository.ProposalRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end (MockMvc, real Postgres) of creating a Receivable from a Commercial Order with a CONFIRMED booking:
 * an authorized financial user (holding {@code financial:receivable:create}) creates the Receivable, which
 * preserves the commercial origin (Order/Proposal/Opportunity/Lead/Customer) and total and starts OPEN. Only
 * orders with a confirmed booking can originate one (else 422), at most one active Receivable per order (else
 * 409), an unknown order is 404, and callers without the financial scope are 403/401. Creating a Receivable
 * registers no Payment and creates no Commission, Invoice or Booking data.
 */
class ReceivableApiIntegrationTest extends AbstractIntegrationTest {

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

    // Receivables + customers are derived data of the commercial parents; delete them first (FK-safe) so the
    // shared container is left clean for later test classes that wipe orders/leads.
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
    void financeCreatesAReceivableFromAConfirmedOrderPreservingOriginAndTotal() throws Exception {
        UUID order = confirmedOrder("Conf", "CONFIRMED");
        String fin = finance();

        String created = mvc.perform(post("/api/receivables")
                        .header("Authorization", "Bearer " + fin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\",\"dueDate\":\"2026-07-15\",\"paymentNotes\":\"boleto\"}"
                                .formatted(order)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String receivableId = JsonPath.read(created, "$.id");

        String body = mvc.perform(get("/api/receivables/" + receivableId).header("Authorization", "Bearer " + fin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.commercialOrderId").value(order.toString()))
                .andExpect(jsonPath("$.totalAmount").value(500.00))
                .andExpect(jsonPath("$.dueDate").value("2026-07-15"))
                .andExpect(jsonPath("$.paymentNotes").value("boleto"))
                .andExpect(jsonPath("$.customerName").value("Lead Conf"))
                // The consultation fields: paid / outstanding / overdue and the readable commercial references.
                .andExpect(jsonPath("$.amountPaid").value(0))
                .andExpect(jsonPath("$.outstandingAmount").value(500.00))
                .andExpect(jsonPath("$.overdue").value(false)) // due date is in the future
                .andExpect(jsonPath("$.proposalReference").value("Proposta Conf"))
                .andExpect(jsonPath("$.opportunityReference").value("Conf"))
                // No explicit schedule → one full-amount installment, OPEN, and no payments yet.
                .andExpect(jsonPath("$.installments.length()").value(1))
                .andExpect(jsonPath("$.installments[0].id").exists())
                .andExpect(jsonPath("$.installments[0].number").value(1))
                .andExpect(jsonPath("$.installments[0].amount").value(500.00))
                .andExpect(jsonPath("$.installments[0].dueDate").value("2026-07-15"))
                .andExpect(jsonPath("$.installments[0].status").value("OPEN"))
                .andExpect(jsonPath("$.payments.length()").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // The contract exposes receivable + commercial-origin data only — never Commission, bank-reconciliation
        // or tax-invoice data.
        Map<String, Object> detail = JsonPath.read(body, "$");
        assertThat(detail.keySet())
                .containsExactlyInAnyOrder(
                        "id",
                        "commercialOrderId",
                        "orderNumber",
                        "proposalId",
                        "proposalReference",
                        "opportunityId",
                        "opportunityReference",
                        "leadId",
                        "customerId",
                        "customerName",
                        "commercialResponsibleId",
                        "commercialResponsibleName",
                        "financialResponsibleId",
                        "financialResponsibleName",
                        "totalAmount",
                        "amountPaid",
                        "outstandingAmount",
                        "dueDate",
                        "overdue",
                        "paymentNotes",
                        "status",
                        "installments",
                        "payments",
                        "createdAt",
                        "createdByName");
        assertThat(body.toLowerCase()).doesNotContain("commission").doesNotContain("reconcil");

        // No Payment, Commission or Booking row was created by originating the Receivable.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking_requests", Integer.class))
                .isZero();
    }

    @Test
    void theDailyCheckFlagsAPastDueReceivableAsOverdueInDetail() throws Exception {
        UUID order = confirmedOrder("Past", "CONFIRMED");
        String fin = finance();
        String created = mvc.perform(post("/api/receivables")
                        .header("Authorization", "Bearer " + fin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\",\"dueDate\":\"2020-01-01\"}".formatted(order)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String receivableId = JsonPath.read(created, "$.id");

        // Created OPEN — not overdue until the daily check flags it (even though the due date is in the past).
        mvc.perform(get("/api/receivables/" + receivableId).header("Authorization", "Bearer " + fin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.overdue").value(false))
                // The installment itself is identifiable as overdue (past due, unpaid) right away.
                .andExpect(jsonPath("$.installments[0].overdue").value(true));

        overdueJob.markOverdue(LocalDate.now());

        // After the check: the stored status is OVERDUE and the receivable is identifiable as overdue.
        mvc.perform(get("/api/receivables/" + receivableId).header("Authorization", "Bearer " + fin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OVERDUE"))
                .andExpect(jsonPath("$.overdue").value(true))
                .andExpect(jsonPath("$.dueDate").value("2020-01-01"))
                .andExpect(jsonPath("$.outstandingAmount").value(500.00))
                .andExpect(jsonPath("$.installments[0].overdue").value(true));
    }

    @Test
    void aPaidInstallmentIsNotOverdueWhileAnUnpaidPastDueOneIs() throws Exception {
        String fin = finance();
        UUID order = confirmedOrder("MixedDue", "CONFIRMED");
        // Two installments, both due in the past.
        String created = mvc.perform(post("/api/receivables")
                        .header("Authorization", "Bearer " + fin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"commercialOrderId":"%s","dueDate":"2020-01-01","installments":[
                                  {"amount":200.00,"dueDate":"2020-01-01"},
                                  {"amount":300.00,"dueDate":"2020-02-01"}
                                ]}"""
                                        .formatted(order)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String receivableId = JsonPath.read(created, "$.id");
        String firstInstallment = firstInstallmentId(fin, receivableId);

        // Pay the first installment in full → PAID; the second stays unpaid and past due.
        mvc.perform(post("/api/receivables/%s/installments/%s/payments".formatted(receivableId, firstInstallment))
                        .header("Authorization", "Bearer " + fin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodId\":\"%s\",\"amount\":200.00,\"paymentDate\":\"2026-06-01\"}"
                                .formatted(paymentMethodId("PIX"))))
                .andExpect(status().isOk());

        mvc.perform(get("/api/receivables/" + receivableId).header("Authorization", "Bearer " + fin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installments[0].status").value("PAID"))
                .andExpect(jsonPath("$.installments[0].overdue").value(false)) // paid → never overdue
                .andExpect(jsonPath("$.installments[1].status").value("OPEN"))
                .andExpect(jsonPath("$.installments[1].overdue").value(true)); // unpaid + past due
    }

    @Test
    void financeCreatesAMultiInstallmentReceivableSummingToTheTotal() throws Exception {
        UUID order = confirmedOrder("Multi", "CONFIRMED");
        String fin = finance();

        String created = mvc.perform(post("/api/receivables")
                        .header("Authorization", "Bearer " + fin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"commercialOrderId":"%s","dueDate":"2026-07-15","installments":[
                                  {"amount":200.00,"dueDate":"2026-07-15","paymentNotes":"entrada"},
                                  {"amount":300.00,"dueDate":"2026-08-15"}
                                ]}"""
                                        .formatted(order)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String receivableId = JsonPath.read(created, "$.id");

        mvc.perform(get("/api/receivables/" + receivableId).header("Authorization", "Bearer " + fin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(500.00))
                .andExpect(jsonPath("$.installments.length()").value(2))
                .andExpect(jsonPath("$.installments[0].number").value(1))
                .andExpect(jsonPath("$.installments[0].amount").value(200.00))
                .andExpect(jsonPath("$.installments[0].dueDate").value("2026-07-15"))
                .andExpect(jsonPath("$.installments[0].paymentNotes").value("entrada"))
                .andExpect(jsonPath("$.installments[0].status").value("OPEN"))
                .andExpect(jsonPath("$.installments[1].number").value(2))
                .andExpect(jsonPath("$.installments[1].amount").value(300.00))
                .andExpect(jsonPath("$.installments[1].dueDate").value("2026-08-15"))
                .andExpect(jsonPath("$.installments[1].status").value("OPEN"));
    }

    @Test
    void cannotCreateWhenInstallmentsDoNotSumToTheTotal() throws Exception {
        UUID order = confirmedOrder("Mismatch", "CONFIRMED");

        mvc.perform(post("/api/receivables")
                        .header("Authorization", "Bearer " + finance())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"commercialOrderId":"%s","dueDate":"2026-07-15","installments":[
                                  {"amount":100.00,"dueDate":"2026-07-15"},
                                  {"amount":100.00,"dueDate":"2026-08-15"}
                                ]}"""
                                        .formatted(order)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("financial.receivable.installment-schedule-invalid"));
    }

    @Test
    void rejectsANegativeInstallmentAmount() throws Exception {
        UUID order = confirmedOrder("Neg", "CONFIRMED");

        mvc.perform(post("/api/receivables")
                        .header("Authorization", "Bearer " + finance())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"commercialOrderId":"%s","dueDate":"2026-07-15","installments":[
                                  {"amount":600.00,"dueDate":"2026-07-15"},
                                  {"amount":-100.00,"dueDate":"2026-08-15"}
                                ]}"""
                                        .formatted(order)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAMissingInstallmentDueDate() throws Exception {
        UUID order = confirmedOrder("NoDue", "CONFIRMED");

        mvc.perform(post("/api/receivables")
                        .header("Authorization", "Bearer " + finance())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"commercialOrderId\":\"%s\",\"dueDate\":\"2026-07-15\",\"installments\":[{\"amount\":500.00}]}"
                                        .formatted(order)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cannotCreateFromAnOrderWhoseBookingIsNotConfirmed() throws Exception {
        UUID order = confirmedOrder("NotConf", "PARTIALLY_CONFIRMED");

        mvc.perform(post("/api/receivables")
                        .header("Authorization", "Bearer " + finance())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\",\"dueDate\":\"2026-07-15\"}".formatted(order)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("financial.receivable.order-not-confirmed"));
    }

    @Test
    void cannotCreateWhenTheBookingHasNotStartedYet() throws Exception {
        UUID order = confirmedOrder("NoBooking", null);

        mvc.perform(post("/api/receivables")
                        .header("Authorization", "Bearer " + finance())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\",\"dueDate\":\"2026-07-15\"}".formatted(order)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("financial.receivable.order-not-confirmed"));
    }

    @Test
    void cannotCreateADuplicateActiveReceivableForTheSameOrder() throws Exception {
        UUID order = confirmedOrder("Dup", "CONFIRMED");
        String fin = finance();
        String first = mvc.perform(post("/api/receivables")
                        .header("Authorization", "Bearer " + fin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\",\"dueDate\":\"2026-07-15\"}".formatted(order)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String firstId = JsonPath.read(first, "$.id");

        mvc.perform(post("/api/receivables")
                        .header("Authorization", "Bearer " + fin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\",\"dueDate\":\"2026-08-15\"}".formatted(order)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("financial.receivable.already-exists"))
                .andExpect(jsonPath("$.fields[0].field").value("receivableId"))
                .andExpect(jsonPath("$.fields[0].message").value(firstId));
    }

    @Test
    void returnsNotFoundForAnUnknownSourceOrder() throws Exception {
        mvc.perform(post("/api/receivables")
                        .header("Authorization", "Bearer " + finance())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\",\"dueDate\":\"2026-07-15\"}"
                                .formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("financial.receivable.order-not-found"));
    }

    @Test
    void rejectsAMissingDueDate() throws Exception {
        UUID order = confirmedOrder("NoDue", "CONFIRMED");

        mvc.perform(post("/api/receivables")
                        .header("Authorization", "Bearer " + finance())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\"}".formatted(order)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnauthenticatedCreate() throws Exception {
        mvc.perform(post("/api/receivables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\",\"dueDate\":\"2026-07-15\"}"
                                .formatted(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aCommercialUserWithoutFinancialCreateScopeIsForbidden() throws Exception {
        UUID order = confirmedOrder("Forbidden", "CONFIRMED");

        // The seller has no financial scope at all → 403 on both create and read.
        String seller = login("vendedor", "vendedor123");
        mvc.perform(post("/api/receivables")
                        .header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\",\"dueDate\":\"2026-07-15\"}".formatted(order)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/receivables").header("Authorization", "Bearer " + seller))
                .andExpect(status().isForbidden());
    }

    @Test
    void theManagerConsultsButCannotCreate() throws Exception {
        UUID order = confirmedOrder("Consult", "CONFIRMED");
        String fin = finance();
        mvc.perform(post("/api/receivables")
                        .header("Authorization", "Bearer " + fin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\",\"dueDate\":\"2026-07-15\"}".formatted(order)))
                .andExpect(status().isCreated());

        // The commercial manager holds financial:receivable:read:all (consultation) but not create.
        String manager = login("comercial", "comercial123");
        mvc.perform(post("/api/receivables")
                        .header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\",\"dueDate\":\"2026-07-15\"}".formatted(order)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/receivables").header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void eligibleOrdersListsConfirmedOrdersWithoutAnActiveReceivableThenExcludesThem() throws Exception {
        UUID order = confirmedOrder("Elig", "CONFIRMED");
        String fin = finance();

        // Before creating: the confirmed order is eligible.
        mvc.perform(get("/api/receivables/eligible-orders").header("Authorization", "Bearer " + fin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].orderId").value(order.toString()))
                .andExpect(jsonPath("$[0].customerName").value("Lead Elig"))
                .andExpect(jsonPath("$[0].total").value(500.00));

        mvc.perform(post("/api/receivables")
                        .header("Authorization", "Bearer " + fin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\",\"dueDate\":\"2026-07-15\"}".formatted(order)))
                .andExpect(status().isCreated());

        // After creating: it is no longer eligible (it now has an active Receivable).
        mvc.perform(get("/api/receivables/eligible-orders").header("Authorization", "Bearer " + fin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void financeRegistersAFullPaymentSettlingTheReceivable() throws Exception {
        String fin = finance();
        String receivableId = createReceivable(fin, confirmedOrder("Pay", "CONFIRMED"));
        String installmentId = firstInstallmentId(fin, receivableId);
        String pix = paymentMethodId("PIX");

        String body = mvc.perform(
                        post("/api/receivables/%s/installments/%s/payments".formatted(receivableId, installmentId))
                                .header("Authorization", "Bearer " + fin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"paymentMethodId\":\"%s\",\"amount\":500.00,\"paymentDate\":\"2026-06-01\",\"note\":\"pix recebido\"}"
                                                .formatted(pix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.amountPaid").value(500.00))
                .andExpect(jsonPath("$.outstandingAmount").value(0))
                .andExpect(jsonPath("$.installments[0].status").value("PAID"))
                .andExpect(jsonPath("$.payments.length()").value(1))
                .andExpect(jsonPath("$.payments[0].amount").value(500.00))
                .andExpect(jsonPath("$.payments[0].paymentDate").value("2026-06-01"))
                .andExpect(jsonPath("$.payments[0].paymentMethodCode").value("PIX"))
                .andExpect(jsonPath("$.payments[0].paymentMethodLabel").value("Pix"))
                .andExpect(jsonPath("$.payments[0].installmentNumber").value(1))
                .andExpect(jsonPath("$.payments[0].note").value("pix recebido"))
                .andExpect(jsonPath("$.payments[0].registeredByName").value("financeiro"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        // Still receivable + commercial-origin data only — never Commission or bank-reconciliation data.
        assertThat(body.toLowerCase()).doesNotContain("commission").doesNotContain("reconcil");

        // The list reflects the denormalized payment standing.
        mvc.perform(get("/api/receivables?status=PAID").header("Authorization", "Bearer " + fin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].amountPaid").value(500.00))
                .andExpect(jsonPath("$.content[0].outstandingAmount").value(0))
                .andExpect(jsonPath("$.content[0].lastPaymentDate").value("2026-06-01"));

        // Exactly one Payment row; no Booking/Commission created by registering it.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM receivable_payments", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking_requests", Integer.class))
                .isZero();
    }

    @Test
    void registeringEachInstallmentMovesPartiallyPaidThenPaid() throws Exception {
        String fin = finance();
        UUID order = confirmedOrder("Multi2", "CONFIRMED");
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
        String receivableId = JsonPath.read(created, "$.id");
        String cash = paymentMethodId("CASH");
        String detail = mvc.perform(get("/api/receivables/" + receivableId).header("Authorization", "Bearer " + fin))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String first = JsonPath.read(detail, "$.installments[0].id");
        String second = JsonPath.read(detail, "$.installments[1].id");

        mvc.perform(post("/api/receivables/%s/installments/%s/payments".formatted(receivableId, first))
                        .header("Authorization", "Bearer " + fin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodId\":\"%s\",\"amount\":200.00,\"paymentDate\":\"2026-06-01\"}"
                                .formatted(cash)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.amountPaid").value(200.00))
                .andExpect(jsonPath("$.outstandingAmount").value(300.00));

        mvc.perform(post("/api/receivables/%s/installments/%s/payments".formatted(receivableId, second))
                        .header("Authorization", "Bearer " + fin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodId\":\"%s\",\"amount\":300.00,\"paymentDate\":\"2026-06-02\"}"
                                .formatted(cash)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.amountPaid").value(500.00))
                .andExpect(jsonPath("$.outstandingAmount").value(0))
                .andExpect(jsonPath("$.payments.length()").value(2));
    }

    @Test
    void registersAPartialPaymentLeavingThePartiallyPaidThenSettlesIt() throws Exception {
        String fin = finance();
        String receivableId = createReceivable(fin, confirmedOrder("Partial", "CONFIRMED"));
        String installmentId = firstInstallmentId(fin, receivableId);
        String pix = paymentMethodId("PIX");

        // First partial payment: 200 of the 500 single installment.
        mvc.perform(post("/api/receivables/%s/installments/%s/payments".formatted(receivableId, installmentId))
                        .header("Authorization", "Bearer " + fin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodId\":\"%s\",\"amount\":200.00,\"paymentDate\":\"2026-06-01\"}"
                                .formatted(pix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.amountPaid").value(200.00))
                .andExpect(jsonPath("$.outstandingAmount").value(300.00))
                .andExpect(jsonPath("$.installments[0].status").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.installments[0].amountPaid").value(200.00))
                .andExpect(jsonPath("$.installments[0].outstanding").value(300.00))
                .andExpect(jsonPath("$.payments.length()").value(1));

        // Second partial payment for the remaining 300 settles the installment and the Receivable.
        mvc.perform(post("/api/receivables/%s/installments/%s/payments".formatted(receivableId, installmentId))
                        .header("Authorization", "Bearer " + fin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodId\":\"%s\",\"amount\":300.00,\"paymentDate\":\"2026-06-02\"}"
                                .formatted(pix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.amountPaid").value(500.00))
                .andExpect(jsonPath("$.outstandingAmount").value(0))
                .andExpect(jsonPath("$.installments[0].status").value("PAID"))
                .andExpect(jsonPath("$.installments[0].amountPaid").value(500.00))
                .andExpect(jsonPath("$.installments[0].outstanding").value(0))
                .andExpect(jsonPath("$.payments.length()").value(2));
    }

    @Test
    void rejectsAPaymentExceedingTheOutstanding() throws Exception {
        String fin = finance();
        String receivableId = createReceivable(fin, confirmedOrder("Exceeds", "CONFIRMED"));
        String installmentId = firstInstallmentId(fin, receivableId);

        // The single installment is 500; 600 exceeds the outstanding (overpayment is out of scope).
        mvc.perform(post("/api/receivables/%s/installments/%s/payments".formatted(receivableId, installmentId))
                        .header("Authorization", "Bearer " + fin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodId\":\"%s\",\"amount\":600.00,\"paymentDate\":\"2026-06-01\"}"
                                .formatted(paymentMethodId("PIX"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("financial.payment.exceeds-outstanding"));
    }

    @Test
    void rejectsAPaymentForAnUnknownInstallment() throws Exception {
        String fin = finance();
        String receivableId = createReceivable(fin, confirmedOrder("Unknown", "CONFIRMED"));

        mvc.perform(post("/api/receivables/%s/installments/%s/payments".formatted(receivableId, UUID.randomUUID()))
                        .header("Authorization", "Bearer " + fin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodId\":\"%s\",\"amount\":500.00,\"paymentDate\":\"2026-06-01\"}"
                                .formatted(paymentMethodId("PIX"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("financial.payment.installment-not-found"));
    }

    @Test
    void rejectsASecondPaymentForAnAlreadyPaidInstallment() throws Exception {
        String fin = finance();
        String receivableId = createReceivable(fin, confirmedOrder("Twice", "CONFIRMED"));
        String installmentId = firstInstallmentId(fin, receivableId);
        String pix = paymentMethodId("PIX");
        String payment = "{\"paymentMethodId\":\"%s\",\"amount\":500.00,\"paymentDate\":\"2026-06-01\"}".formatted(pix);

        mvc.perform(post("/api/receivables/%s/installments/%s/payments".formatted(receivableId, installmentId))
                        .header("Authorization", "Bearer " + fin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payment))
                .andExpect(status().isOk());
        mvc.perform(post("/api/receivables/%s/installments/%s/payments".formatted(receivableId, installmentId))
                        .header("Authorization", "Bearer " + fin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payment))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("financial.payment.installment-not-payable"));
    }

    @Test
    void rejectsAPaymentWithAnUnknownMethod() throws Exception {
        String fin = finance();
        String receivableId = createReceivable(fin, confirmedOrder("NoMethod", "CONFIRMED"));
        String installmentId = firstInstallmentId(fin, receivableId);

        mvc.perform(post("/api/receivables/%s/installments/%s/payments".formatted(receivableId, installmentId))
                        .header("Authorization", "Bearer " + fin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodId\":\"%s\",\"amount\":500.00,\"paymentDate\":\"2026-06-01\"}"
                                .formatted(UUID.randomUUID())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("financial.payment.method-not-available"));
    }

    @Test
    void rejectsAPaymentWithAnInactiveMethod() throws Exception {
        String fin = finance();
        String receivableId = createReceivable(fin, confirmedOrder("Inactive", "CONFIRMED"));
        String installmentId = firstInstallmentId(fin, receivableId);
        UUID inactive = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO payment_methods (id, code, label, active, sort_order) "
                        + "VALUES (cast(? as uuid), ?, ?, FALSE, 99)",
                inactive.toString(),
                "TEMP_INACTIVE_" + phoneSeq,
                "Inativa");

        mvc.perform(post("/api/receivables/%s/installments/%s/payments".formatted(receivableId, installmentId))
                        .header("Authorization", "Bearer " + fin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodId\":\"%s\",\"amount\":500.00,\"paymentDate\":\"2026-06-01\"}"
                                .formatted(inactive)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("financial.payment.method-not-available"));
        jdbc.update("DELETE FROM payment_methods WHERE id = cast(? as uuid)", inactive.toString());
    }

    @Test
    void rejectsAFuturePaymentDate() throws Exception {
        String fin = finance();
        String receivableId = createReceivable(fin, confirmedOrder("Future", "CONFIRMED"));
        String installmentId = firstInstallmentId(fin, receivableId);

        mvc.perform(post("/api/receivables/%s/installments/%s/payments".formatted(receivableId, installmentId))
                        .header("Authorization", "Bearer " + fin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodId\":\"%s\",\"amount\":500.00,\"paymentDate\":\"2999-01-01\"}"
                                .formatted(paymentMethodId("PIX"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsANonPositivePaymentAmount() throws Exception {
        String fin = finance();
        String receivableId = createReceivable(fin, confirmedOrder("Zero", "CONFIRMED"));
        String installmentId = firstInstallmentId(fin, receivableId);

        mvc.perform(post("/api/receivables/%s/installments/%s/payments".formatted(receivableId, installmentId))
                        .header("Authorization", "Bearer " + fin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodId\":\"%s\",\"amount\":0,\"paymentDate\":\"2026-06-01\"}"
                                .formatted(paymentMethodId("PIX"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theManagerCannotRegisterAPayment() throws Exception {
        String fin = finance();
        String receivableId = createReceivable(fin, confirmedOrder("MgrPay", "CONFIRMED"));
        String installmentId = firstInstallmentId(fin, receivableId);

        // The commercial manager holds financial:receivable:read:all (consultation) but not payment:register.
        String manager = login("comercial", "comercial123");
        mvc.perform(post("/api/receivables/%s/installments/%s/payments".formatted(receivableId, installmentId))
                        .header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodId\":\"%s\",\"amount\":500.00,\"paymentDate\":\"2026-06-01\"}"
                                .formatted(paymentMethodId("PIX"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsAnUnauthenticatedPayment() throws Exception {
        mvc.perform(post("/api/receivables/%s/installments/%s/payments".formatted(UUID.randomUUID(), UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodId\":\"%s\",\"amount\":500.00,\"paymentDate\":\"2026-06-01\"}"
                                .formatted(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void financeReversesAPaymentReturningTheReceivableToOpenKeepingTheHistory() throws Exception {
        String fin = finance();
        UUID order = confirmedOrder("Reverse", "CONFIRMED");
        String receivableId = createReceivable(fin, order);
        String installmentId = firstInstallmentId(fin, receivableId);
        String paymentId = registerFullPayment(fin, receivableId, installmentId);

        // The booking-confirmed order reflected PAID after the full payment; reversing returns it to OPEN.
        assertThat(orderFinancialStatus(order)).isEqualTo("PAID");

        String body = mvc.perform(post("/api/receivables/%s/payments/%s/reversals".formatted(receivableId, paymentId))
                        .header("Authorization", "Bearer " + fin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"lançamento duplicado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.amountPaid").value(0))
                .andExpect(jsonPath("$.outstandingAmount").value(500.00))
                .andExpect(jsonPath("$.installments[0].status").value("OPEN"))
                // The payment stays in history, marked reversed with its reason and who/when.
                .andExpect(jsonPath("$.payments.length()").value(1))
                .andExpect(jsonPath("$.payments[0].reversed").value(true))
                .andExpect(jsonPath("$.payments[0].reversalReason").value("lançamento duplicado"))
                .andExpect(jsonPath("$.payments[0].reversedByName").value("financeiro"))
                .andExpect(jsonPath("$.payments[0].reversedAt").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        // Reversing creates no Commission/bank-reconciliation data; the order reflection is back to OPEN.
        assertThat(body.toLowerCase()).doesNotContain("commission").doesNotContain("reconcil");
        assertThat(orderFinancialStatus(order)).isEqualTo("OPEN");

        // The payment row is kept (not deleted), now carrying the reversal stamp.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM receivable_payments", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM receivable_payments WHERE reversed_at IS NOT NULL", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void reversingOneOfTwoPaymentsReturnsTheReceivableToPartiallyPaid() throws Exception {
        String fin = finance();
        UUID order = confirmedOrder("RevPartial", "CONFIRMED");
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
        String receivableId = JsonPath.read(created, "$.id");
        String detail = mvc.perform(get("/api/receivables/" + receivableId).header("Authorization", "Bearer " + fin))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String first = JsonPath.read(detail, "$.installments[0].id");
        String second = JsonPath.read(detail, "$.installments[1].id");
        String pix = paymentMethodId("PIX");
        payInstallment(fin, receivableId, first, "200.00", "2026-06-01", pix);
        String secondPaymentBody = payInstallment(fin, receivableId, second, "300.00", "2026-06-02", pix);
        assertThat(JsonPath.read(secondPaymentBody, "$.status").toString()).isEqualTo("PAID");
        String secondPaymentId = paymentIdForInstallment(secondPaymentBody, second);

        mvc.perform(post("/api/receivables/%s/payments/%s/reversals".formatted(receivableId, secondPaymentId))
                        .header("Authorization", "Bearer " + fin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"estorno parcela 2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.amountPaid").value(200.00))
                .andExpect(jsonPath("$.outstandingAmount").value(300.00))
                .andExpect(jsonPath("$.installments[1].status").value("OPEN"))
                .andExpect(jsonPath("$.payments.length()").value(2)); // both kept; one reversed
    }

    @Test
    void reversingAnAlreadyReversedPaymentIsRejected() throws Exception {
        String fin = finance();
        String receivableId = createReceivable(fin, confirmedOrder("RevTwice", "CONFIRMED"));
        String installmentId = firstInstallmentId(fin, receivableId);
        String paymentId = registerFullPayment(fin, receivableId, installmentId);

        mvc.perform(post("/api/receivables/%s/payments/%s/reversals".formatted(receivableId, paymentId))
                        .header("Authorization", "Bearer " + fin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"primeiro estorno\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/receivables/%s/payments/%s/reversals".formatted(receivableId, paymentId))
                        .header("Authorization", "Bearer " + fin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"segundo estorno\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("financial.payment.already-reversed"));
    }

    @Test
    void rejectsAReversalOfAnUnknownPayment() throws Exception {
        String fin = finance();
        String receivableId = createReceivable(fin, confirmedOrder("RevUnknown", "CONFIRMED"));

        mvc.perform(post("/api/receivables/%s/payments/%s/reversals".formatted(receivableId, UUID.randomUUID()))
                        .header("Authorization", "Bearer " + fin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"estorno\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("financial.payment.not-found"));
    }

    @Test
    void rejectsAReversalWithoutAReason() throws Exception {
        String fin = finance();
        String receivableId = createReceivable(fin, confirmedOrder("RevNoReason", "CONFIRMED"));
        String installmentId = firstInstallmentId(fin, receivableId);
        String paymentId = registerFullPayment(fin, receivableId, installmentId);

        mvc.perform(post("/api/receivables/%s/payments/%s/reversals".formatted(receivableId, paymentId))
                        .header("Authorization", "Bearer " + fin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theManagerCannotReverseAPayment() throws Exception {
        String fin = finance();
        String receivableId = createReceivable(fin, confirmedOrder("RevMgr", "CONFIRMED"));
        String installmentId = firstInstallmentId(fin, receivableId);
        String paymentId = registerFullPayment(fin, receivableId, installmentId);

        // The commercial manager holds financial:receivable:read:all (consultation) but not payment:reverse.
        String manager = login("comercial", "comercial123");
        mvc.perform(post("/api/receivables/%s/payments/%s/reversals".formatted(receivableId, paymentId))
                        .header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"estorno\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsAnUnauthenticatedReversal() throws Exception {
        mvc.perform(post("/api/receivables/%s/payments/%s/reversals".formatted(UUID.randomUUID(), UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"estorno\"}"))
                .andExpect(status().isUnauthorized());
    }

    private String registerFullPayment(String token, String receivableId, String installmentId) throws Exception {
        String body =
                payInstallment(token, receivableId, installmentId, "500.00", "2026-06-01", paymentMethodId("PIX"));
        return JsonPath.read(body, "$.payments[0].id");
    }

    private String payInstallment(
            String token, String receivableId, String installmentId, String amount, String date, String methodId)
            throws Exception {
        return mvc.perform(post("/api/receivables/%s/installments/%s/payments".formatted(receivableId, installmentId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodId\":\"%s\",\"amount\":%s,\"paymentDate\":\"%s\"}"
                                .formatted(methodId, amount, date)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String paymentIdForInstallment(String detailBody, String installmentId) {
        java.util.List<Map<String, Object>> payments = JsonPath.read(detailBody, "$.payments");
        return payments.stream()
                .filter(p -> installmentId.equals(p.get("installmentId")))
                .map(p -> (String) p.get("id"))
                .findFirst()
                .orElseThrow();
    }

    private String orderFinancialStatus(UUID orderId) {
        return jdbc.queryForObject(
                "SELECT financial_status FROM commercial_orders WHERE id = cast(? as uuid)",
                String.class,
                orderId.toString());
    }

    private String createReceivable(String token, UUID order) throws Exception {
        String created = mvc.perform(post("/api/receivables")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\",\"dueDate\":\"2026-07-15\"}".formatted(order)))
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

    /** Seeds an Order with the given booking status (responsible = the manager), inserting its commercial chain. */
    private UUID confirmedOrder(String name, String bookingStatus) {
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
                        cast(? as uuid), cast(? as uuid), cast(? as uuid), 'PENDING_BOOKING', ?, 500.00, 500.00,
                        cast(? as uuid), cast(? as uuid))
                """,
                orderId.toString(),
                proposal.toString(),
                opportunity.toString(),
                lead.toString(),
                MANAGER.toString(),
                bookingStatus,
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
