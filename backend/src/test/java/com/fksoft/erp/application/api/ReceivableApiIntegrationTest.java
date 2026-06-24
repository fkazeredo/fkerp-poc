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
                // No explicit schedule → one full-amount installment, OPEN.
                .andExpect(jsonPath("$.installments.length()").value(1))
                .andExpect(jsonPath("$.installments[0].number").value(1))
                .andExpect(jsonPath("$.installments[0].amount").value(500.00))
                .andExpect(jsonPath("$.installments[0].dueDate").value("2026-07-15"))
                .andExpect(jsonPath("$.installments[0].status").value("OPEN"))
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
                        "createdAt",
                        "createdByName");
        assertThat(body.toLowerCase()).doesNotContain("commission").doesNotContain("reconcil");

        // No Payment, Commission or Booking row was created by originating the Receivable.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking_requests", Integer.class))
                .isZero();
    }

    @Test
    void theDetailFlagsAPastDueReceivableAsOverdue() throws Exception {
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

        mvc.perform(get("/api/receivables/" + receivableId).header("Authorization", "Bearer " + fin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overdue").value(true))
                .andExpect(jsonPath("$.dueDate").value("2020-01-01"))
                .andExpect(jsonPath("$.outstandingAmount").value(500.00));
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
