package com.fksoft.erp.application.api;

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
import com.fksoft.erp.infra.security.TokenService;
import com.jayway.jsonpath.JsonPath;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end (MockMvc, real Postgres) of the operational pending-items worklist for Opportunities:
 * every category is surfaced with its reasons, non-pending Opportunities (recently active, LOST) are
 * excluded, a recent activity rescues an old Opportunity from the "without recent activity" reason, and
 * visibility is respected (a representative sees only their own pending; Finance has no access).
 * Opportunities, their source leads and their activities are inserted directly via JDBC to set up varied
 * ages/stages/dates cheaply. The staleness window is 14 days (see {@code OpportunityPendingReasons}).
 */
class OpportunityPendingApiIntegrationTest extends AbstractIntegrationTest {

    private static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID VENDEDOR = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID REPRESENTANTE = UUID.fromString("00000000-0000-0000-0000-000000000003");

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
        opportunities.deleteAll(); // FK to leads — clear opportunities first
        leads.deleteAll();
        originId = origins.findByActiveTrueOrderBySortOrderAsc().get(0).id();
        phoneSeq = 0;

        // No activity and old → WITHOUT_RECENT_ACTIVITY (PRODUCT_FIT isolates it from the stuck reasons).
        insertOpportunity("NoActivityOld", "PRODUCT_FIT", MANAGER, 30, null, null);

        // Recent but with a past-due next action → OVERDUE_NEXT_ACTION.
        insertOpportunity("OverdueAction", "PRODUCT_FIT", MANAGER, 1, daysFromNow(-2), null);

        // Stuck in the first stages past the window → STUCK_IN_NEW / STUCK_IN_DISCOVERY.
        insertOpportunity("StuckNew", "NEW_OPPORTUNITY", MANAGER, 30, null, null);
        insertOpportunity("StuckDiscovery", "DISCOVERY", MANAGER, 30, null, null);

        // Ready for a proposal (no time threshold) → READY_FOR_PROPOSAL.
        insertOpportunity("ReadyProposal", "READY_FOR_PROPOSAL", MANAGER, 1, null, null);

        // Expected closing date in the past → EXPECTED_CLOSE_OVERDUE.
        insertOpportunity("CloseOverdue", "PRODUCT_FIT", MANAGER, 1, null, daysFromNow(-2));

        // Recently created and active, nothing overdue → not pending.
        UUID activeRecent = insertOpportunity("ActiveRecent", "PRODUCT_FIT", MANAGER, 1, null, null);
        insertActivity(activeRecent, 1);

        // Old, but a recent activity rescues it from WITHOUT_RECENT_ACTIVITY → not pending.
        UUID oldButActive = insertOpportunity("OldButActive", "PRODUCT_FIT", MANAGER, 30, null, null);
        insertActivity(oldButActive, 1);

        // LOST is terminal and never pending, even when old with no activity.
        insertOpportunity("LostExcluded", "LOST", MANAGER, 30, null, null);

        // Owned by the representative, stuck in NEW → pending, but only the rep (and read-all) sees it.
        insertOpportunity("RepPending", "NEW_OPPORTUNITY", REPRESENTANTE, 30, null, null);
    }

    @Test
    void managerSeesEveryPendingCategoryAndExcludesNonPending() throws Exception {
        mvc.perform(get("/api/opportunities/pending")
                        .param("size", "50")
                        .header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", hasItem("NoActivityOld")))
                .andExpect(jsonPath("$.content[*].name", hasItem("OverdueAction")))
                .andExpect(jsonPath("$.content[*].name", hasItem("StuckNew")))
                .andExpect(jsonPath("$.content[*].name", hasItem("StuckDiscovery")))
                .andExpect(jsonPath("$.content[*].name", hasItem("ReadyProposal")))
                .andExpect(jsonPath("$.content[*].name", hasItem("CloseOverdue")))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("ActiveRecent"))))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("OldButActive"))))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("LostExcluded"))));
    }

    @Test
    void reasonsAreReportedPerOpportunity() throws Exception {
        mvc.perform(get("/api/opportunities/pending")
                        .param("size", "50")
                        .header("Authorization", "Bearer " + manager()))
                .andExpect(jsonPath(
                        "$.content[?(@.name=='NoActivityOld')].reasons[*]", hasItem("WITHOUT_RECENT_ACTIVITY")))
                .andExpect(jsonPath("$.content[?(@.name=='OverdueAction')].reasons[*]", hasItem("OVERDUE_NEXT_ACTION")))
                .andExpect(jsonPath("$.content[?(@.name=='StuckNew')].reasons[*]", hasItem("STUCK_IN_NEW")))
                .andExpect(jsonPath("$.content[?(@.name=='StuckDiscovery')].reasons[*]", hasItem("STUCK_IN_DISCOVERY")))
                .andExpect(jsonPath("$.content[?(@.name=='ReadyProposal')].reasons[*]", hasItem("READY_FOR_PROPOSAL")))
                .andExpect(
                        jsonPath("$.content[?(@.name=='CloseOverdue')].reasons[*]", hasItem("EXPECTED_CLOSE_OVERDUE")));
    }

    @Test
    void representativeSeesOnlyOwnPending() throws Exception {
        String rep = login("representante", "representante123");
        mvc.perform(get("/api/opportunities/pending").param("size", "50").header("Authorization", "Bearer " + rep))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", hasItem("RepPending")))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("StuckNew"))))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("StuckDiscovery"))))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("NoActivityOld"))));
    }

    @Test
    void paginatesWithEnvelope() throws Exception {
        mvc.perform(get("/api/opportunities/pending").param("size", "2").header("Authorization", "Bearer " + manager()))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void financeHasNoAccessToPending() throws Exception {
        String fin = login("financeiro", "financeiro123");
        mvc.perform(get("/api/opportunities/pending").header("Authorization", "Bearer " + fin))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsWithoutAReadScope() throws Exception {
        // Holding only the create scope does not grant the pending worklist.
        String noRead = tokens.issueAccessToken(new AuthenticatedUser(VENDEDOR, "v", Set.of("crm:opportunity:create")));
        mvc.perform(get("/api/opportunities/pending").header("Authorization", "Bearer " + noRead))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnauthenticated() throws Exception {
        mvc.perform(get("/api/opportunities/pending")).andExpect(status().isUnauthorized());
    }

    private static Date daysFromNow(int days) {
        return Date.valueOf(LocalDate.now().plusDays(days));
    }

    private UUID insertOpportunity(
            String name,
            String stage,
            UUID responsibleId,
            int createdDaysAgo,
            Date nextActionDate,
            Date expectedCloseDate) {
        UUID leadId = insertLead(name, responsibleId);
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO opportunities (id, version, lead_id, name, origin_id, responsible_person_id,
                                           main_interest, stage, loss_reason, next_action_date, expected_close_date,
                                           created_at, updated_at, created_by, updated_by)
                VALUES (cast(? as uuid), 0, cast(? as uuid), ?, cast(? as uuid), cast(? as uuid),
                        ?, ?, ?, ?, ?, now() - make_interval(days => ?), now(), cast(? as uuid), cast(? as uuid))
                """,
                id.toString(),
                leadId.toString(),
                name,
                originId.toString(),
                responsibleId == null ? null : responsibleId.toString(),
                "Pacote " + name,
                stage,
                "LOST".equals(stage) ? "OTHER" : null,
                nextActionDate,
                expectedCloseDate,
                createdDaysAgo,
                MANAGER.toString(),
                MANAGER.toString());
        return id;
    }

    private void insertActivity(UUID opportunityId, int occurredDaysAgo) {
        jdbc.update(
                """
                INSERT INTO opportunity_activities (id, opportunity_id, type_id, result_id, description,
                                                    occurred_at, registered_by)
                VALUES (cast(? as uuid), cast(? as uuid),
                        (SELECT id FROM opportunity_activity_types WHERE code = 'PHONE_CALL'),
                        (SELECT id FROM opportunity_activity_results WHERE code = 'NEEDS_FOLLOW_UP'), 'x',
                        now() - make_interval(days => ?), cast(? as uuid))
                """,
                UUID.randomUUID().toString(),
                opportunityId.toString(),
                occurredDaysAgo,
                MANAGER.toString());
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
