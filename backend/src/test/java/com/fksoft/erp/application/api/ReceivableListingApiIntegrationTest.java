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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end (MockMvc, real Postgres) of the operational Receivable list: the receivables that require financial
 * follow-up, with the required operational fields and filters. The settled PAID/CANCELLED receivables are
 * excluded by default (shown only when explicitly filtered); overdue receivables stay visible as operational
 * problems. Visibility respects the profile (representatives get 403). The contract carries receivable +
 * commercial-origin data only — never Commission or bank-reconciliation data.
 */
class ReceivableListingApiIntegrationTest extends AbstractIntegrationTest {

    private static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID FINANCE = UUID.fromString("00000000-0000-0000-0000-000000000005");

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
    private final Map<String, Long> orderNumberByPayer = new HashMap<>();

    @BeforeEach
    void seed() {
        wipe();
        originId = origins.findByActiveTrueOrderBySortOrderAsc().get(0).id();
        phoneSeq = 0;
        orderNumberByPayer.clear();
        // A spread of receivables: operational (OPEN / PARTIALLY_PAID / OVERDUE) + settled (PAID / CANCELLED).
        seedReceivable("Alpha Cliente", "OPEN", LocalDate.of(2026, 12, 1), "1000.00", MANAGER, FINANCE);
        seedReceivable("Beta Cliente", "PARTIALLY_PAID", LocalDate.of(2020, 1, 1), "2000.00", SELLER, null);
        seedReceivable("Gamma Cliente", "OVERDUE", LocalDate.of(2020, 6, 1), "3000.00", MANAGER, FINANCE);
        seedReceivable("Delta Cliente", "PAID", LocalDate.of(2026, 12, 1), "4000.00", MANAGER, FINANCE);
        seedReceivable("Epsilon Cliente", "CANCELLED", LocalDate.of(2026, 12, 1), "5000.00", MANAGER, FINANCE);
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
    void defaultListShowsOnlyTheOperationalReceivablesExcludingPaidAndCancelled() throws Exception {
        mvc.perform(get("/api/receivables").header("Authorization", "Bearer " + finance()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath(
                        "$.content[*].customerName",
                        org.hamcrest.Matchers.containsInAnyOrder("Alpha Cliente", "Beta Cliente", "Gamma Cliente")))
                .andExpect(jsonPath(
                        "$.content[*].status", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("PAID"))))
                .andExpect(jsonPath(
                        "$.content[*].status", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("CANCELLED"))));
    }

    @Test
    void statusFilterRevealsThePaidAndCancelledReceivables() throws Exception {
        String fin = finance();
        mvc.perform(get("/api/receivables?status=PAID").header("Authorization", "Bearer " + fin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("PAID"))
                .andExpect(jsonPath("$.content[0].customerName").value("Delta Cliente"));
        mvc.perform(get("/api/receivables?status=CANCELLED").header("Authorization", "Bearer " + fin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("CANCELLED"));
    }

    @Test
    void overdueReceivablesStayVisibleByDefaultAndCanBeFilteredAlone() throws Exception {
        String fin = finance();
        // Overdue is the stored OVERDUE status (the daily check's authoritative result), not the reference due
        // date: Gamma (OVERDUE) is flagged; Alpha (OPEN, future) and Beta (PARTIALLY_PAID, past-due but not yet
        // flagged) are not — both stay visible by default as operational receivables.
        mvc.perform(get("/api/receivables").header("Authorization", "Bearer " + fin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.customerName == 'Gamma Cliente')].overdue")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.content[?(@.customerName == 'Alpha Cliente')].overdue")
                        .value(org.hamcrest.Matchers.hasItem(false)))
                .andExpect(jsonPath("$.content[?(@.customerName == 'Beta Cliente')].overdue")
                        .value(org.hamcrest.Matchers.hasItem(false)));
        // overdueOnly keeps just the OVERDUE-status ones (Gamma), not the past-due-but-not-flagged Beta.
        mvc.perform(get("/api/receivables?overdueOnly=true").header("Authorization", "Bearer " + fin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].customerName").value("Gamma Cliente"));
    }

    @Test
    void filtersByDueDatePeriod() throws Exception {
        mvc.perform(get("/api/receivables?dueFrom=2020-05-01&dueTo=2020-12-31")
                        .header("Authorization", "Bearer " + finance()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].customerName").value("Gamma Cliente"));
    }

    @Test
    void filtersByAmountRange() throws Exception {
        mvc.perform(get("/api/receivables?amountMin=2500&amountMax=3500")
                        .header("Authorization", "Bearer " + finance()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].customerName").value("Gamma Cliente"))
                .andExpect(jsonPath("$.content[0].totalAmount").value(3000.00));
    }

    @Test
    void filtersByPayerNameCaseInsensitively() throws Exception {
        mvc.perform(get("/api/receivables?payer=gamma").header("Authorization", "Bearer " + finance()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].customerName").value("Gamma Cliente"));
    }

    @Test
    void filtersByFinancialAndCommercialResponsible() throws Exception {
        String fin = finance();
        // Financial responsible = financeiro → Alpha + Gamma (Beta has none).
        mvc.perform(get("/api/receivables?financialResponsible=" + FINANCE).header("Authorization", "Bearer " + fin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath(
                        "$.content[*].customerName",
                        org.hamcrest.Matchers.containsInAnyOrder("Alpha Cliente", "Gamma Cliente")));
        // Commercial responsible = seller → only Beta.
        mvc.perform(get("/api/receivables?commercialResponsible=" + SELLER).header("Authorization", "Bearer " + fin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].customerName").value("Beta Cliente"));
    }

    @Test
    void filtersBySourceOrderNumber() throws Exception {
        long alphaOrder = orderNumberByPayer.get("Alpha Cliente");
        mvc.perform(get("/api/receivables?orderNumber=" + alphaOrder).header("Authorization", "Bearer " + finance()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].customerName").value("Alpha Cliente"))
                .andExpect(jsonPath("$.content[0].orderNumber").value((int) alphaOrder));
    }

    @Test
    void filtersByCreationPeriod() throws Exception {
        String fin = finance();
        // All seeded with created_at 2026-06-01 → a later lower bound returns nothing.
        mvc.perform(get("/api/receivables?createdFrom=2026-07-01").header("Authorization", "Bearer " + fin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/receivables?createdFrom=2026-06-01&createdTo=2026-06-01")
                        .header("Authorization", "Bearer " + fin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void exposesTheOperationalFieldsAndNoCommissionOrReconciliationData() throws Exception {
        String body = mvc.perform(get("/api/receivables?status=OPEN").header("Authorization", "Bearer " + finance()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].amountPaid").value(0))
                .andExpect(jsonPath("$.content[0].outstandingAmount").value(1000.00))
                .andExpect(jsonPath("$.content[0].overdue").value(false))
                .andExpect(jsonPath("$.content[0].commercialResponsibleName").value("comercial"))
                .andExpect(jsonPath("$.content[0].financialResponsibleName").value("financeiro"))
                .andExpect(jsonPath("$.content[0].lastPaymentDate").value(org.hamcrest.Matchers.nullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> item = JsonPath.read(body, "$.content[0]");
        assertThat(item.keySet())
                .containsExactlyInAnyOrder(
                        "id",
                        "commercialOrderId",
                        "orderNumber",
                        "customerName",
                        "totalAmount",
                        "amountPaid",
                        "outstandingAmount",
                        "status",
                        "dueDate",
                        "overdue",
                        "commercialResponsibleId",
                        "commercialResponsibleName",
                        "financialResponsibleId",
                        "financialResponsibleName",
                        "createdAt",
                        "lastPaymentDate");
        assertThat(body.toLowerCase())
                .doesNotContain("commission")
                .doesNotContain("commiss")
                .doesNotContain("reconcil");
    }

    @Test
    void aRepresentativeCannotSeeTheFinancialList() throws Exception {
        mvc.perform(get("/api/receivables")
                        .header("Authorization", "Bearer " + login("representante", "representante123")))
                .andExpect(status().isForbidden());
    }

    private void seedReceivable(
            String payerName, String status, LocalDate dueDate, String total, UUID commercialResp, UUID financialResp) {
        UUID lead = insertLead(payerName);
        insertCustomer(lead, payerName);
        UUID opp = insertOpportunity(payerName, lead);
        UUID proposal = insertProposal(payerName, opp, lead);
        UUID orderId = UUID.randomUUID();
        UUID customerId =
                jdbc.queryForObject("SELECT id FROM customers WHERE lead_id = ?::uuid", UUID.class, lead.toString());
        jdbc.update(
                """
                INSERT INTO commercial_orders (id, version, number, proposal_id, opportunity_id, lead_id,
                                               responsible_person_id, status, booking_status, subtotal, total,
                                               created_by, updated_by)
                VALUES (cast(? as uuid), 0, nextval('commercial_order_number_seq'), cast(? as uuid),
                        cast(? as uuid), cast(? as uuid), cast(? as uuid), 'PENDING_BOOKING', 'CONFIRMED',
                        cast(? as numeric), cast(? as numeric), cast(? as uuid), cast(? as uuid))
                """,
                orderId.toString(),
                proposal.toString(),
                opp.toString(),
                lead.toString(),
                MANAGER.toString(),
                total,
                total,
                MANAGER.toString(),
                MANAGER.toString());
        long orderNumber = jdbc.queryForObject(
                "SELECT number FROM commercial_orders WHERE id = ?::uuid", Long.class, orderId.toString());
        orderNumberByPayer.put(payerName, orderNumber);
        jdbc.update(
                """
                INSERT INTO receivables (id, version, commercial_order_id, proposal_id, opportunity_id, lead_id,
                                         customer_id, commercial_responsible_person_id, financial_responsible_person_id,
                                         total_amount, due_date, status, created_at, updated_at, created_by, updated_by)
                VALUES (cast(? as uuid), 0, cast(? as uuid), cast(? as uuid), cast(? as uuid), cast(? as uuid),
                        cast(? as uuid), cast(? as uuid), cast(? as uuid), cast(? as numeric), cast(? as date), ?,
                        '2026-06-01T10:00:00Z'::timestamptz, '2026-06-01T10:00:00Z'::timestamptz,
                        cast(? as uuid), cast(? as uuid))
                """,
                UUID.randomUUID().toString(),
                orderId.toString(),
                proposal.toString(),
                opp.toString(),
                lead.toString(),
                customerId.toString(),
                commercialResp == null ? null : commercialResp.toString(),
                financialResp == null ? null : financialResp.toString(),
                total,
                dueDate.toString(),
                status,
                FINANCE.toString(),
                FINANCE.toString());
    }

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
                        'ACCEPTED', 0, 0, cast(? as uuid), cast(? as uuid))
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
                name,
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
