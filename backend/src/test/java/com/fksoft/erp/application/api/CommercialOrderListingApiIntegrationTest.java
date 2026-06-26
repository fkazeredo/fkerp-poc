package com.fksoft.erp.application.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end (MockMvc, real Postgres) of the operational Commercial Order list: it carries the operational
 * columns (no Booking/Finance/Commission data), respects commercial ownership (a representative sees only
 * their own Orders, a manager sees all), filters by status / responsible / amount / booking need / creation
 * period, and excludes cancelled Orders by default. Each item carries the sequential order number.
 */
class CommercialOrderListingApiIntegrationTest extends AbstractIntegrationTest {

    private static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID REPRESENTANTE = UUID.fromString("00000000-0000-0000-0000-000000000003");

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
        orders.deleteAll();
        proposals.deleteAll();
        opportunities.deleteAll();
        leads.deleteAll();
        originId = origins.findByActiveTrueOrderBySortOrderAsc().get(0).id();
        phoneSeq = 0;
    }

    @AfterEach
    void cleanup() {
        orders.deleteAll();
        proposals.deleteAll();
        opportunities.deleteAll();
        leads.deleteAll();
    }

    @Test
    void exposesOnlyOperationalColumns() throws Exception {
        seedOrder("Contrato Acme", MANAGER, "PENDING_BOOKING", new BigDecimal("2500.00"), Instant.now());

        String body = mvc.perform(get("/api/orders").header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].proposalTitle").value("Contrato Acme"))
                .andExpect(jsonPath("$.content[0].opportunityName").value("Opp Contrato Acme"))
                .andExpect(jsonPath("$.content[0].total").value(2500.0))
                .andExpect(jsonPath("$.content[0].requiresBooking").value(true))
                .andExpect(jsonPath("$.content[0].number").isNumber())
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<Map<String, Object>> content = JsonPath.read(body, "$.content");
        // The list item contract is exactly these fields — the booking and financial status are reflected (read
        // only), but never Receivable / Payment / Commission detail.
        org.assertj.core.api.Assertions.assertThat(content.get(0).keySet())
                .containsExactlyInAnyOrder(
                        "id",
                        "number",
                        "proposalId",
                        "proposalTitle",
                        "opportunityId",
                        "opportunityName",
                        "status",
                        "responsibleId",
                        "responsibleName",
                        "unassigned",
                        "total",
                        "requiresBooking",
                        "bookingStatus",
                        "financialStatus",
                        "commissionStatus",
                        "createdAt");
    }

    @Test
    void representativeSeesOnlyOwnOrdersAndManagerSeesAll() throws Exception {
        seedOrder("DoGerente", MANAGER, "PENDING_BOOKING", new BigDecimal("100.00"), Instant.now());
        seedOrder("DoRep", REPRESENTANTE, "PENDING_BOOKING", new BigDecimal("100.00"), Instant.now());

        // Representative: only their own.
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + login("representante", "representante123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].proposalTitle", hasItem("DoRep")))
                .andExpect(jsonPath("$.content[*].proposalTitle", not(hasItem("DoGerente"))));

        // Manager: all.
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].proposalTitle", hasItem("DoRep")))
                .andExpect(jsonPath("$.content[*].proposalTitle", hasItem("DoGerente")));
    }

    @Test
    void filtersByResponsible() throws Exception {
        seedOrder("DoGerente", MANAGER, "PENDING_BOOKING", new BigDecimal("100.00"), Instant.now());
        seedOrder("DoRep", REPRESENTANTE, "PENDING_BOOKING", new BigDecimal("100.00"), Instant.now());

        mvc.perform(get("/api/orders?responsible=" + REPRESENTANTE).header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].proposalTitle", contains("DoRep")));
    }

    @Test
    void filtersByBookingNeed() throws Exception {
        seedOrder("PrecisaReserva", MANAGER, "PENDING_BOOKING", new BigDecimal("100.00"), Instant.now());
        seedOrder("SemReserva", MANAGER, "BOOKING_NOT_REQUIRED", new BigDecimal("100.00"), Instant.now());

        mvc.perform(get("/api/orders?bookingNeed=REQUIRED").header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].proposalTitle", contains("PrecisaReserva")));
        mvc.perform(get("/api/orders?bookingNeed=NOT_REQUIRED").header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].proposalTitle", contains("SemReserva")));
    }

    @Test
    void filtersByAmountRange() throws Exception {
        seedOrder("Barato", MANAGER, "PENDING_BOOKING", new BigDecimal("100.00"), Instant.now());
        seedOrder("Caro", MANAGER, "PENDING_BOOKING", new BigDecimal("9000.00"), Instant.now());

        mvc.perform(get("/api/orders?totalMin=1000").header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].proposalTitle", contains("Caro")));
    }

    @Test
    void excludesCancelledByDefaultButShowsThemWhenFiltered() throws Exception {
        seedOrder("Ativo", MANAGER, "PENDING_BOOKING", new BigDecimal("100.00"), Instant.now());
        seedOrder("Cancelado", MANAGER, "CANCELLED", new BigDecimal("100.00"), Instant.now());

        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].proposalTitle", hasItem("Ativo")))
                .andExpect(jsonPath("$.content[*].proposalTitle", not(hasItem("Cancelado"))));

        mvc.perform(get("/api/orders?status=CANCELLED").header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].proposalTitle", contains("Cancelado")));
    }

    @Test
    void rejectsUnauthenticated() throws Exception {
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void financeCanListOrdersWithOrderReadAll() throws Exception {
        // financeiro now holds sales:order:read:all (to see source orders for Receivables, Sprint 5) → may list.
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + login("financeiro", "financeiro123")))
                .andExpect(status().isOk());
    }

    /** Inserts a lead + opportunity + proposal + order chain; the proposal title is the client-facing summary. */
    private void seedOrder(String title, UUID responsibleId, String status, BigDecimal total, Instant createdAt) {
        UUID lead = insertLead(title, responsibleId);
        UUID opp = insertOpportunity("Opp " + title, lead, responsibleId);
        UUID proposal = insertProposal(title, opp, lead, responsibleId);
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO commercial_orders (id, version, number, proposal_id, opportunity_id, lead_id,
                                               responsible_person_id, status, subtotal, total,
                                               created_at, updated_at, created_by, updated_by)
                VALUES (cast(? as uuid), 0, nextval('commercial_order_number_seq'), cast(? as uuid),
                        cast(? as uuid), cast(? as uuid), cast(? as uuid), ?, ?, ?, ?, now(),
                        cast(? as uuid), cast(? as uuid))
                """,
                id.toString(),
                proposal.toString(),
                opp.toString(),
                lead.toString(),
                responsibleId == null ? null : responsibleId.toString(),
                status,
                total,
                total,
                java.sql.Timestamp.from(createdAt),
                MANAGER.toString(),
                MANAGER.toString());
    }

    private UUID insertProposal(String title, UUID opportunityId, UUID leadId, UUID responsibleId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO proposals (id, version, opportunity_id, lead_id, responsible_person_id, title,
                                       status, subtotal, total, created_by, updated_by)
                VALUES (cast(? as uuid), 0, cast(? as uuid), cast(? as uuid), cast(? as uuid), ?, 'ACCEPTED',
                        0, 0, cast(? as uuid), cast(? as uuid))
                """,
                id.toString(),
                opportunityId.toString(),
                leadId.toString(),
                responsibleId == null ? null : responsibleId.toString(),
                title,
                MANAGER.toString(),
                MANAGER.toString());
        return id;
    }

    private UUID insertOpportunity(String name, UUID leadId, UUID responsibleId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO opportunities (id, version, lead_id, name, origin_id, responsible_person_id,
                                           main_interest, stage, loss_reason, created_by, updated_by)
                VALUES (cast(? as uuid), 0, cast(? as uuid), ?, cast(? as uuid), cast(? as uuid),
                        ?, ?, NULL, cast(? as uuid), cast(? as uuid))
                """,
                id.toString(),
                leadId.toString(),
                name,
                originId.toString(),
                responsibleId == null ? null : responsibleId.toString(),
                "Pacote " + name,
                "WON",
                MANAGER.toString(),
                MANAGER.toString());
        return id;
    }

    private UUID insertLead(String name, UUID responsibleId) {
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
                responsibleId == null ? null : responsibleId.toString(),
                MANAGER.toString(),
                MANAGER.toString());
        return id;
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
