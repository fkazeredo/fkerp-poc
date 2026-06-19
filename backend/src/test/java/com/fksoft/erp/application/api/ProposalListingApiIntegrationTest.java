package com.fksoft.erp.application.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fksoft.erp.AbstractIntegrationTest;
import com.fksoft.erp.domain.crm.model.OpportunityStage;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.repository.OpportunityRepository;
import com.fksoft.erp.domain.crm.repository.OriginRepository;
import com.fksoft.erp.domain.sales.model.ProposalStatus;
import com.fksoft.erp.domain.sales.repository.ProposalRepository;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end (MockMvc, real Postgres) of the operational Proposal list (Slice 4): the default view excludes
 * the terminal-negative outcomes (REJECTED/EXPIRED/CANCELLED) unless explicitly filtered, the filters by
 * status / responsible / creation period / validity period / source Opportunity / amount range and the
 * free-text search (title + source Opportunity name) all narrow correctly, visibility is enforced (a
 * representative sees only their own), the contract carries commercial-offer data only (no Booking/Payment/
 * Commission), and unauthenticated/unauthorized callers are rejected. Proposals are inserted via JDBC to
 * set up varied statuses, owners, totals, validity and creation dates (the lifecycle transitions that would
 * reach the inactive statuses are later slices).
 */
class ProposalListingApiIntegrationTest extends AbstractIntegrationTest {

    private static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID REPRESENTANTE = UUID.fromString("00000000-0000-0000-0000-000000000003");

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
        proposals.deleteAll(); // FK to opportunities/leads
        opportunities.deleteAll();
        leads.deleteAll();
        originId = origins.findByActiveTrueOrderBySortOrderAsc().get(0).id();
        phoneSeq = 0;
    }

    @Test
    void defaultListExcludesTerminalNegativeProposals() throws Exception {
        insertProposal("Ativa", MANAGER, ProposalStatus.DRAFT);
        insertProposal("Cancelada", MANAGER, ProposalStatus.CANCELLED);
        insertProposal("Rejeitada", MANAGER, ProposalStatus.REJECTED);
        insertProposal("Expirada", MANAGER, ProposalStatus.EXPIRED);

        mvc.perform(get("/api/proposals").header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title", hasItem("Ativa")))
                .andExpect(jsonPath("$.content[*].title", not(hasItem("Cancelada"))))
                .andExpect(jsonPath("$.content[*].title", not(hasItem("Rejeitada"))))
                .andExpect(jsonPath("$.content[*].title", not(hasItem("Expirada"))));
    }

    @Test
    void filtersByStatusIncludingInactiveWhenAsked() throws Exception {
        insertProposal("Ativa", MANAGER, ProposalStatus.DRAFT);
        insertProposal("Cancelada", MANAGER, ProposalStatus.CANCELLED);

        mvc.perform(get("/api/proposals?status=CANCELLED").header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title", contains("Cancelada")));
        mvc.perform(get("/api/proposals?status=DRAFT").header("Authorization", "Bearer " + manager()))
                .andExpect(jsonPath("$.content[*].title", contains("Ativa")));
    }

    @Test
    void filtersByResponsible() throws Exception {
        insertProposal("DoGerente", MANAGER, ProposalStatus.DRAFT);
        insertProposal("DoRep", REPRESENTANTE, ProposalStatus.DRAFT);

        mvc.perform(get("/api/proposals?responsible=" + REPRESENTANTE).header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title", contains("DoRep")));
    }

    @Test
    void filtersByCreationPeriod() throws Exception {
        Instant now = Instant.now();
        insertProposalCreatedAt("Antiga", MANAGER, now.minus(20, ChronoUnit.DAYS));
        insertProposalCreatedAt("Recente", MANAGER, now);

        LocalDate from = LocalDate.now(ZoneOffset.UTC).minusDays(2);
        mvc.perform(get("/api/proposals?createdFrom=" + from).header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title", contains("Recente")));
    }

    @Test
    void filtersByValidityPeriod() throws Exception {
        insertProposalValidUntil(
                "ValeCedo", MANAGER, LocalDate.now(ZoneOffset.UTC).plusDays(5));
        insertProposalValidUntil(
                "ValeTarde", MANAGER, LocalDate.now(ZoneOffset.UTC).plusDays(60));

        LocalDate from = LocalDate.now(ZoneOffset.UTC).plusDays(30);
        mvc.perform(get("/api/proposals?validFrom=" + from).header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title", contains("ValeTarde")));
    }

    @Test
    void filtersBySourceOpportunity() throws Exception {
        Seeded a = insertOpportunity("OppA", MANAGER);
        Seeded b = insertOpportunity("OppB", MANAGER);
        insertProposalFor("DaOppA", a, MANAGER, ProposalStatus.DRAFT, new BigDecimal("100.00"), null, Instant.now());
        insertProposalFor("DaOppB", b, MANAGER, ProposalStatus.DRAFT, new BigDecimal("100.00"), null, Instant.now());

        mvc.perform(get("/api/proposals?opportunityId=" + a.opportunityId())
                        .header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title", contains("DaOppA")));
    }

    @Test
    void filtersByAmountRange() throws Exception {
        insertProposalTotal("Barata", MANAGER, new BigDecimal("100.00"));
        insertProposalTotal("Cara", MANAGER, new BigDecimal("9000.00"));

        mvc.perform(get("/api/proposals?totalMin=1000").header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title", contains("Cara")));
        mvc.perform(get("/api/proposals?totalMax=1000").header("Authorization", "Bearer " + manager()))
                .andExpect(jsonPath("$.content[*].title", contains("Barata")));
    }

    @Test
    void searchesByTitleAndSourceOpportunityName() throws Exception {
        Seeded acme = insertOpportunity("Acme Viagens", MANAGER);
        insertProposalFor(
                "Pacote Premium", acme, MANAGER, ProposalStatus.DRAFT, new BigDecimal("100.00"), null, Instant.now());
        insertProposal("Outra coisa", MANAGER, ProposalStatus.DRAFT);

        // By Proposal title.
        mvc.perform(get("/api/proposals?q=premium").header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title", contains("Pacote Premium")));
        // By source Opportunity name.
        mvc.perform(get("/api/proposals?q=acme").header("Authorization", "Bearer " + manager()))
                .andExpect(jsonPath("$.content[*].title", contains("Pacote Premium")));
    }

    @Test
    void representativeSeesOnlyOwnProposals() throws Exception {
        insertProposal("DoGerente", MANAGER, ProposalStatus.DRAFT);
        insertProposal("DoRep", REPRESENTANTE, ProposalStatus.DRAFT);

        mvc.perform(get("/api/proposals")
                        .header("Authorization", "Bearer " + login("representante", "representante123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title", hasItem("DoRep")))
                .andExpect(jsonPath("$.content[*].title", not(hasItem("DoGerente"))));
    }

    @Test
    void exposesOnlyOperationalColumns() throws Exception {
        Seeded acme = insertOpportunity("Acme Viagens", MANAGER);
        insertProposalFor(
                "Contrato", acme, MANAGER, ProposalStatus.DRAFT, new BigDecimal("2500.00"), null, Instant.now());

        String body = mvc.perform(get("/api/proposals").header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].opportunityName").value("Acme Viagens"))
                .andExpect(jsonPath("$.content[0].total").value(2500.0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<Map<String, Object>> content = JsonPath.read(body, "$.content");
        // The list item contract is exactly these fields — no Sale / Booking / Payment / Commission.
        org.assertj.core.api.Assertions.assertThat(content.get(0).keySet())
                .containsExactlyInAnyOrder(
                        "id",
                        "opportunityId",
                        "opportunityName",
                        "title",
                        "status",
                        "responsibleId",
                        "responsibleName",
                        "unassigned",
                        "total",
                        "validUntil",
                        "createdAt",
                        "updatedAt");
    }

    @Test
    void rejectsUnauthenticatedList() throws Exception {
        mvc.perform(get("/api/proposals")).andExpect(status().isUnauthorized());
    }

    @Test
    void financeHasNoAccessToList() throws Exception {
        mvc.perform(get("/api/proposals").header("Authorization", "Bearer " + login("financeiro", "financeiro123")))
                .andExpect(status().isForbidden());
    }

    @Test
    void filterByStatusNeverBypassesVisibility() throws Exception {
        insertProposal("DoGerente", MANAGER, ProposalStatus.DRAFT);
        // Even asking for an explicit status, a representative still only sees their own (none here).
        mvc.perform(get("/api/proposals?status=DRAFT")
                        .header("Authorization", "Bearer " + login("representante", "representante123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", containsInAnyOrder()));
    }

    // --- seeding helpers ---

    private record Seeded(UUID opportunityId, UUID leadId) {}

    private UUID insertProposal(String title, UUID responsibleId, ProposalStatus status) {
        Seeded src = insertOpportunity(title, responsibleId);
        return insertProposalFor(title, src, responsibleId, status, new BigDecimal("100.00"), null, Instant.now());
    }

    private UUID insertProposalCreatedAt(String title, UUID responsibleId, Instant createdAt) {
        Seeded src = insertOpportunity(title, responsibleId);
        return insertProposalFor(
                title, src, responsibleId, ProposalStatus.DRAFT, new BigDecimal("100.00"), null, createdAt);
    }

    private UUID insertProposalValidUntil(String title, UUID responsibleId, LocalDate validUntil) {
        Seeded src = insertOpportunity(title, responsibleId);
        return insertProposalFor(
                title, src, responsibleId, ProposalStatus.DRAFT, new BigDecimal("100.00"), validUntil, Instant.now());
    }

    private UUID insertProposalTotal(String title, UUID responsibleId, BigDecimal total) {
        Seeded src = insertOpportunity(title, responsibleId);
        return insertProposalFor(title, src, responsibleId, ProposalStatus.DRAFT, total, null, Instant.now());
    }

    private UUID insertProposalFor(
            String title,
            Seeded src,
            UUID responsibleId,
            ProposalStatus status,
            BigDecimal total,
            LocalDate validUntil,
            Instant createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO proposals (id, version, opportunity_id, lead_id, responsible_person_id, title,
                                       valid_until, status, subtotal, total, created_at, updated_at,
                                       created_by, updated_by)
                VALUES (cast(? as uuid), 0, cast(? as uuid), cast(? as uuid), cast(? as uuid), ?,
                        ?, ?, ?, ?, ?, now(), cast(? as uuid), cast(? as uuid))
                """,
                id.toString(),
                src.opportunityId().toString(),
                src.leadId().toString(),
                responsibleId == null ? null : responsibleId.toString(),
                title,
                validUntil == null ? null : java.sql.Date.valueOf(validUntil),
                status.name(),
                total,
                total,
                java.sql.Timestamp.from(createdAt),
                MANAGER.toString(),
                MANAGER.toString());
        return id;
    }

    private Seeded insertOpportunity(String name, UUID responsibleId) {
        UUID leadId = insertLead(name, responsibleId);
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
                OpportunityStage.READY_FOR_PROPOSAL.name(),
                MANAGER.toString(),
                MANAGER.toString());
        return new Seeded(id, leadId);
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
