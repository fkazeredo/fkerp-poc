package com.fksoft.erp.application.api;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.fksoft.erp.domain.identity.AuthenticatedUser;
import com.fksoft.erp.domain.sales.repository.CommercialOrderRepository;
import com.fksoft.erp.domain.sales.repository.ProposalRepository;
import com.fksoft.erp.infra.security.TokenService;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end (MockMvc, real Postgres) of the minimum Proposal indicators: the period-scoped volume (total /
 * by status / by responsible / proposed amount / accepted amount / rejected count) plus the current
 * operational snapshot (waiting for review, waiting for customer decision), visibility scoping (a
 * representative's numbers cover only their own Proposals), the read scopes (Finance has none → 403), and
 * that the contract carries commercial-offer figures only — never Sale, Order, Booking, Financial or
 * Commission fields. Proposals (and their source opportunities/leads) are inserted directly via JDBC to set
 * up varied statuses/owners/totals/dates cheaply.
 */
class ProposalIndicatorsApiIntegrationTest extends AbstractIntegrationTest {

    private static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID VENDEDOR = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID REPRESENTANTE = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Autowired
    private CommercialOrderRepository ordersRepo;

    @Autowired
    private ProposalRepository proposals;

    @Autowired
    private OpportunityRepository opportunities;

    @Autowired
    private LeadRepository leads;

    @Autowired
    private OriginRepository origins;

    @Autowired
    private TokenService tokens;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID originId;
    private int phoneSeq;

    @BeforeEach
    void seed() {
        wipe();
        originId = origins.findByActiveTrueOrderBySortOrderAsc().get(0).id();
        phoneSeq = 0;

        // Manager-owned mix across statuses, recent.
        insertProposal("Draft1", "DRAFT", MANAGER, "1000.00", 0);
        insertProposal("Review1", "READY_FOR_REVIEW", MANAGER, "2000.00", 0); // waiting for review
        insertProposal("Sent1", "SENT", MANAGER, "3000.00", 0); // waiting for customer decision
        insertProposal("Accepted1", "ACCEPTED", MANAGER, "4000.00", 0);
        insertProposal("Rejected1", "REJECTED", MANAGER, "500.00", 0);
        insertProposal("SentUnassigned", "SENT", null, "1500.00", 0); // unassigned + waiting decision
        insertProposal("AcceptedRep", "ACCEPTED", REPRESENTANTE, "2500.00", 0); // representative's own

        // Sent two months ago — excluded from the period volume, still in the operational snapshot.
        insertProposal("OldSent", "SENT", MANAGER, "7000.00", 60);
    }

    // This class creates proposals/opportunities/leads; clean them all up (FK-safe order) so later test
    // classes — which wipe those parents — are not blocked by the FKs on the shared container.
    @AfterEach
    void cleanup() {
        wipe();
    }

    private void wipe() {
        ordersRepo.deleteAll(); // FK to proposals
        proposals.deleteAll(); // FK to opportunities/leads
        opportunities.deleteAll();
        leads.deleteAll();
    }

    @Test
    void managerSeesGlobalVolume() throws Exception {
        String body = managerIndicators();
        assertThat(JsonPath.<Integer>read(body, "$.total")).isEqualTo(8);
        assertThat(JsonPath.<Integer>read(body, "$.rejectedCount")).isEqualTo(1);
        assertThat(decimal(body, "$.proposedAmount")).isEqualByComparingTo("21500"); // sum of all totals
        assertThat(decimal(body, "$.acceptedAmount")).isEqualByComparingTo("6500"); // 4000 + 2500
        // By status.
        assertThat(JsonPath.<List<Integer>>read(body, "$.byStatus[?(@.status=='SENT')].count"))
                .containsExactly(3); // Sent1 + SentUnassigned + OldSent
        assertThat(JsonPath.<List<Integer>>read(body, "$.byStatus[?(@.status=='ACCEPTED')].count"))
                .containsExactly(2);
        assertThat(JsonPath.<List<Integer>>read(body, "$.byStatus[?(@.status=='REJECTED')].count"))
                .containsExactly(1);
        // By responsible.
        assertThat(JsonPath.<List<Integer>>read(body, "$.byResponsible[?(@.responsibleName=='comercial')].count"))
                .containsExactly(6);
        assertThat(JsonPath.<List<Integer>>read(body, "$.byResponsible[?(@.responsibleName == null)].count"))
                .containsExactly(1); // unassigned (SentUnassigned)
    }

    @Test
    void managerSeesTheOperationalSnapshot() throws Exception {
        String body = managerIndicators();
        assertThat(JsonPath.<Integer>read(body, "$.waitingForReview")).isEqualTo(1); // Review1
        assertThat(JsonPath.<Integer>read(body, "$.waitingForCustomerDecision"))
                .isEqualTo(3); // Sent1 + SentUnassigned + OldSent
    }

    @Test
    void thePeriodNarrowsVolumeButNotTheSnapshot() throws Exception {
        // A period starting tomorrow contains nothing created yet → volume zero…
        String tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1).toString();
        String body = mvc.perform(get("/api/proposals/indicators")
                        .param("createdFrom", tomorrow)
                        .header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(JsonPath.<Integer>read(body, "$.total")).isEqualTo(0);
        assertThat(decimal(body, "$.proposedAmount")).isEqualByComparingTo("0");
        assertThat(decimal(body, "$.acceptedAmount")).isEqualByComparingTo("0");
        // …but the operational snapshot ignores the period.
        assertThat(JsonPath.<Integer>read(body, "$.waitingForReview")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(body, "$.waitingForCustomerDecision")).isEqualTo(3);
    }

    @Test
    void representativeIndicatorsAreScopedToTheirOwnProposals() throws Exception {
        String rep = login("representante", "representante123");
        String body = mvc.perform(get("/api/proposals/indicators").header("Authorization", "Bearer " + rep))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(JsonPath.<Integer>read(body, "$.total")).isEqualTo(1); // only AcceptedRep
        assertThat(decimal(body, "$.acceptedAmount")).isEqualByComparingTo("2500");
        assertThat(JsonPath.<Integer>read(body, "$.waitingForReview")).isEqualTo(0);
        assertThat(JsonPath.<Integer>read(body, "$.waitingForCustomerDecision")).isEqualTo(0);
        // The manager's Proposals never leak into a representative's numbers.
        mvc.perform(get("/api/proposals/indicators").header("Authorization", "Bearer " + rep))
                .andExpect(jsonPath("$.byResponsible[*].responsibleName", not(hasItem("comercial"))));
    }

    @Test
    void exposesOnlyCommercialIndicatorFields() throws Exception {
        Map<String, Object> root = JsonPath.read(managerIndicators(), "$");
        // The contract is exactly these fields — no Sale / Order / Booking / Financial / Commission data.
        assertThat(root.keySet())
                .containsExactlyInAnyOrder(
                        "total",
                        "byStatus",
                        "byResponsible",
                        "proposedAmount",
                        "acceptedAmount",
                        "rejectedCount",
                        "waitingForReview",
                        "waitingForCustomerDecision");
    }

    @Test
    void financeHasNoAccessToIndicators() throws Exception {
        String fin = login("financeiro", "financeiro123");
        mvc.perform(get("/api/proposals/indicators").header("Authorization", "Bearer " + fin))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsWithoutAReadScope() throws Exception {
        String noRead = tokens.issueAccessToken(new AuthenticatedUser(VENDEDOR, "v", Set.of("sales:proposal:create")));
        mvc.perform(get("/api/proposals/indicators").header("Authorization", "Bearer " + noRead))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnauthenticated() throws Exception {
        mvc.perform(get("/api/proposals/indicators")).andExpect(status().isUnauthorized());
    }

    private String managerIndicators() throws Exception {
        return mvc.perform(get("/api/proposals/indicators").header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private static BigDecimal decimal(String body, String path) {
        return new BigDecimal(JsonPath.read(body, path).toString());
    }

    private void insertProposal(String name, String statusValue, UUID responsibleId, String total, int createdDaysAgo) {
        UUID leadId = insertLead(name, responsibleId);
        UUID opportunityId = insertOpportunity(name, leadId);
        jdbc.update(
                """
                INSERT INTO proposals (id, version, opportunity_id, lead_id, responsible_person_id, title,
                                       status, subtotal, total, created_at, updated_at, created_by, updated_by)
                VALUES (cast(? as uuid), 0, cast(? as uuid), cast(? as uuid), cast(? as uuid), ?,
                        ?, ?, ?, now() - make_interval(days => ?), now(), cast(? as uuid), cast(? as uuid))
                """,
                UUID.randomUUID().toString(),
                opportunityId.toString(),
                leadId.toString(),
                responsibleId == null ? null : responsibleId.toString(),
                "Proposta " + name,
                statusValue,
                new BigDecimal(total),
                new BigDecimal(total),
                createdDaysAgo,
                MANAGER.toString(),
                MANAGER.toString());
    }

    private UUID insertOpportunity(String name, UUID leadId) {
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
                MANAGER.toString(),
                "Pacote " + name,
                "READY_FOR_PROPOSAL",
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
