package com.fksoft.erp.application.api;

import static org.assertj.core.api.Assertions.assertThat;
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
 * End-to-end (MockMvc, real Postgres) of the minimum Commercial Order indicators: the period-scoped volume
 * (total / total amount / by responsible) plus the current operational snapshot (pending booking),
 * visibility scoping (a representative's numbers cover only their own Orders), the read scopes (Finance has
 * none → 403), and that the contract carries commercial-order figures only — never Booking, Receivable,
 * Payment or Commission fields. Orders (and their source proposals/opportunities/leads) are inserted
 * directly via JDBC to set up varied statuses/owners/totals/dates cheaply.
 */
class OrderIndicatorsApiIntegrationTest extends AbstractIntegrationTest {

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
        insertOrder("Pend1", "PENDING_BOOKING", MANAGER, "1000.00", 0);
        insertOrder("Pend2", "PENDING_BOOKING", MANAGER, "2000.00", 0);
        insertOrder("NoBook1", "BOOKING_NOT_REQUIRED", MANAGER, "3000.00", 0);
        insertOrder("PendUnassigned", "PENDING_BOOKING", null, "1500.00", 0); // unassigned
        insertOrder("NoBookRep", "BOOKING_NOT_REQUIRED", REPRESENTANTE, "2500.00", 0); // representative's own

        // Pending two months ago — excluded from the period volume, still in the operational snapshot.
        insertOrder("OldPend", "PENDING_BOOKING", MANAGER, "7000.00", 60);
    }

    // This class creates orders/proposals/opportunities/leads; clean them all up (FK-safe order) so later
    // test classes — which wipe those parents — are not blocked by the FKs on the shared container.
    @AfterEach
    void cleanup() {
        wipe();
    }

    private void wipe() {
        ordersRepo.deleteAll();
        proposals.deleteAll();
        opportunities.deleteAll();
        leads.deleteAll();
    }

    @Test
    void managerSeesGlobalVolume() throws Exception {
        String body = managerIndicators();
        assertThat(JsonPath.<Integer>read(body, "$.total")).isEqualTo(6);
        assertThat(decimal(body, "$.totalAmount")).isEqualByComparingTo("17000"); // sum of all order totals
        assertThat(JsonPath.<List<Integer>>read(body, "$.byResponsible[?(@.responsibleName=='comercial')].count"))
                .containsExactly(4); // Pend1 + Pend2 + NoBook1 + OldPend
        assertThat(JsonPath.<List<Integer>>read(body, "$.byResponsible[?(@.responsibleName == null)].count"))
                .containsExactly(1); // unassigned (PendUnassigned)
    }

    @Test
    void managerSeesTheOperationalSnapshot() throws Exception {
        String body = managerIndicators();
        // Pend1 + Pend2 + PendUnassigned + OldPend — the snapshot ignores the period.
        assertThat(JsonPath.<Integer>read(body, "$.pendingBooking")).isEqualTo(4);
    }

    @Test
    void thePeriodNarrowsVolumeButNotTheSnapshot() throws Exception {
        String tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1).toString();
        String body = mvc.perform(get("/api/orders/indicators")
                        .param("createdFrom", tomorrow)
                        .header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(JsonPath.<Integer>read(body, "$.total")).isEqualTo(0);
        assertThat(decimal(body, "$.totalAmount")).isEqualByComparingTo("0");
        // …but the operational snapshot ignores the period.
        assertThat(JsonPath.<Integer>read(body, "$.pendingBooking")).isEqualTo(4);
    }

    @Test
    void representativeIndicatorsAreScopedToTheirOwnOrders() throws Exception {
        String rep = login("representante", "representante123");
        String body = mvc.perform(get("/api/orders/indicators").header("Authorization", "Bearer " + rep))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(JsonPath.<Integer>read(body, "$.total")).isEqualTo(1); // only NoBookRep
        assertThat(decimal(body, "$.totalAmount")).isEqualByComparingTo("2500");
        assertThat(JsonPath.<Integer>read(body, "$.pendingBooking")).isEqualTo(0);
        // The manager's Orders never leak into a representative's numbers.
        mvc.perform(get("/api/orders/indicators").header("Authorization", "Bearer " + rep))
                .andExpect(jsonPath("$.byResponsible[*].responsibleName", not(hasItem("comercial"))));
    }

    @Test
    void exposesOnlyCommercialIndicatorFields() throws Exception {
        Map<String, Object> root = JsonPath.read(managerIndicators(), "$");
        // The contract is exactly these fields — no Booking / Receivable / Payment / Commission data.
        assertThat(root.keySet()).containsExactlyInAnyOrder("total", "totalAmount", "byResponsible", "pendingBooking");
    }

    @Test
    void financeHasNoAccessToIndicators() throws Exception {
        String fin = login("financeiro", "financeiro123");
        mvc.perform(get("/api/orders/indicators").header("Authorization", "Bearer " + fin))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsWithoutAReadScope() throws Exception {
        String noRead = tokens.issueAccessToken(new AuthenticatedUser(VENDEDOR, "v", Set.of("sales:order:create")));
        mvc.perform(get("/api/orders/indicators").header("Authorization", "Bearer " + noRead))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnauthenticated() throws Exception {
        mvc.perform(get("/api/orders/indicators")).andExpect(status().isUnauthorized());
    }

    private String managerIndicators() throws Exception {
        return mvc.perform(get("/api/orders/indicators").header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private static BigDecimal decimal(String body, String path) {
        return new BigDecimal(JsonPath.read(body, path).toString());
    }

    private void insertOrder(String name, String statusValue, UUID responsibleId, String total, int createdDaysAgo) {
        UUID leadId = insertLead(name, responsibleId);
        UUID opportunityId = insertOpportunity(name, leadId);
        UUID proposalId = insertProposal(name, opportunityId, leadId, responsibleId, total);
        jdbc.update(
                """
                INSERT INTO commercial_orders (id, version, number, proposal_id, opportunity_id, lead_id,
                                               responsible_person_id, status, subtotal, total,
                                               created_at, updated_at, created_by, updated_by)
                VALUES (cast(? as uuid), 0, nextval('commercial_order_number_seq'), cast(? as uuid),
                        cast(? as uuid), cast(? as uuid), cast(? as uuid), ?, ?, ?,
                        now() - make_interval(days => ?), now(), cast(? as uuid), cast(? as uuid))
                """,
                UUID.randomUUID().toString(),
                proposalId.toString(),
                opportunityId.toString(),
                leadId.toString(),
                responsibleId == null ? null : responsibleId.toString(),
                statusValue,
                new BigDecimal(total),
                new BigDecimal(total),
                createdDaysAgo,
                MANAGER.toString(),
                MANAGER.toString());
    }

    private UUID insertProposal(String name, UUID opportunityId, UUID leadId, UUID responsibleId, String total) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO proposals (id, version, opportunity_id, lead_id, responsible_person_id, title,
                                       status, subtotal, total, created_by, updated_by)
                VALUES (cast(? as uuid), 0, cast(? as uuid), cast(? as uuid), cast(? as uuid), ?,
                        'ACCEPTED', ?, ?, cast(? as uuid), cast(? as uuid))
                """,
                id.toString(),
                opportunityId.toString(),
                leadId.toString(),
                responsibleId == null ? null : responsibleId.toString(),
                "Proposta " + name,
                new BigDecimal(total),
                new BigDecimal(total),
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
                        ?, ?, NULL, cast(? as uuid), cast(? as uuid))
                """,
                id.toString(),
                leadId.toString(),
                name,
                originId.toString(),
                MANAGER.toString(),
                "Pacote " + name,
                OpportunityStage.WON.name(),
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
