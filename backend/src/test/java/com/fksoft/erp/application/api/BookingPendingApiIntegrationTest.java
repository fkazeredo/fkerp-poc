package com.fksoft.erp.application.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fksoft.erp.AbstractIntegrationTest;
import com.fksoft.erp.domain.crm.model.OpportunityStage;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.repository.OpportunityRepository;
import com.fksoft.erp.domain.crm.repository.OriginRepository;
import com.fksoft.erp.domain.sales.repository.CommercialOrderRepository;
import com.fksoft.erp.domain.sales.repository.ProposalRepository;
import com.jayway.jsonpath.JsonPath;
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
 * End-to-end (MockMvc, real Postgres) of the operational pending-items worklist {@code GET /api/bookings/pending}:
 * it surfaces the Booking Requests that need action, each tagged with its reasons (unassigned operator, pending
 * without attempt, in progress without a recent attempt, a failed item, a requiring-booking item still pending,
 * partially confirmed, overdue next action). Terminal CONFIRMED / CANCELLED requests are excluded. It is gated by
 * the Booking read tiers (the policy narrows visibility): read-all profiles see all, profiles without a booking
 * read tier get 403. The contract carries operational reservation data only — never Financial/Payment/Commission.
 */
class BookingPendingApiIntegrationTest extends AbstractIntegrationTest {

    private static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OPERATOR = UUID.fromString("00000000-0000-0000-0000-000000000006");

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

    private void wipe() {
        jdbc.update("DELETE FROM booking_attempts");
        jdbc.update("DELETE FROM booking_items");
        jdbc.update("DELETE FROM booking_requests");
        jdbc.update("DELETE FROM commercial_order_items");
        orders.deleteAll();
        proposals.deleteAll();
        opportunities.deleteAll();
        leads.deleteAll();
    }

    @Test
    void aPendingUnassignedRequestAppearsWithItsReasons() throws Exception {
        // PENDING, no operator, one requiring item still pending → three reasons.
        UUID req = seedRequest("PENDING", null, null, null, "TRAVEL_PACKAGE:PENDING");

        Map<String, Object> item = pendingItem(operator(), req);
        @SuppressWarnings("unchecked")
        List<String> reasons = (List<String>) item.get("reasons");
        assertThat(reasons)
                .containsExactlyInAnyOrder(
                        "UNASSIGNED_OPERATOR", "PENDING_WITHOUT_ATTEMPT", "HAS_PENDING_REQUIRED_ITEM");
        assertThat(item.get("operatorUnassigned")).isEqualTo(true);
        // Operational data only — no Financial/Payment/Commission fields leak into the contract.
        assertThat(item.keySet())
                .containsExactlyInAnyOrder(
                        "id",
                        "commercialOrderId",
                        "commercialOrderNumber",
                        "proposalId",
                        "proposalTitle",
                        "status",
                        "bookingOperatorId",
                        "bookingOperatorName",
                        "operatorUnassigned",
                        "responsiblePersonId",
                        "responsibleName",
                        "itemsRequiringBooking",
                        "confirmedItems",
                        "failedItems",
                        "lastBookingAttemptAt",
                        "nextActionDate",
                        "createdAt",
                        "updatedAt",
                        "reasons");
    }

    @Test
    void aPartiallyConfirmedRequestAppearsAsPending() throws Exception {
        // One confirmed + one still-pending requiring item, operator assigned, a recent attempt.
        UUID req = seedRequest(
                "PARTIALLY_CONFIRMED",
                OPERATOR,
                "2026-06-14T10:00:00Z",
                null,
                "TRAVEL_PACKAGE:CONFIRMED",
                "CAR_RENTAL:PENDING");

        @SuppressWarnings("unchecked")
        List<String> reasons = (List<String>) pendingItem(operator(), req).get("reasons");
        assertThat(reasons).contains("PARTIALLY_CONFIRMED", "HAS_PENDING_REQUIRED_ITEM");
    }

    @Test
    void aFailedItemMakesTheRequestPending() throws Exception {
        UUID req = seedRequest("FAILED", OPERATOR, "2026-06-14T10:00:00Z", null, "TRAVEL_PACKAGE:FAILED");
        Map<String, Object> item = pendingItem(operator(), req);
        @SuppressWarnings("unchecked")
        List<String> reasons = (List<String>) item.get("reasons");
        assertThat(reasons).contains("HAS_FAILED_ITEM");
        assertThat(item.get("failedItems")).isEqualTo(1);
    }

    @Test
    void anOverdueNextActionMakesTheRequestPending() throws Exception {
        // In progress, recent attempt, but a next action planned in the past.
        UUID req = seedRequest("IN_PROGRESS", OPERATOR, "2026-06-14T10:00:00Z", "2000-01-01", "TRAVEL_PACKAGE:PENDING");
        @SuppressWarnings("unchecked")
        List<String> reasons = (List<String>) pendingItem(operator(), req).get("reasons");
        assertThat(reasons).contains("OVERDUE_NEXT_ACTION");
    }

    @Test
    void anInProgressRequestWithAStaleAttemptIsPending() throws Exception {
        UUID req = seedRequest(
                "IN_PROGRESS", OPERATOR, "2000-01-01T10:00:00Z", null, "TRAVEL_PACKAGE:PENDING"); // very old attempt
        @SuppressWarnings("unchecked")
        List<String> reasons = (List<String>) pendingItem(operator(), req).get("reasons");
        assertThat(reasons).contains("IN_PROGRESS_WITHOUT_RECENT_ATTEMPT");
    }

    @Test
    void confirmedAndCancelledRequestsAreExcluded() throws Exception {
        UUID confirmed = seedRequest("CONFIRMED", OPERATOR, "2026-06-14T10:00:00Z", null, "TRAVEL_PACKAGE:CONFIRMED");
        UUID cancelled = seedRequest("CANCELLED", OPERATOR, null, null, "TRAVEL_PACKAGE:PENDING");
        UUID pending = seedRequest("PENDING", null, null, null, "TRAVEL_PACKAGE:PENDING");

        String body = pendingBody(operator());
        List<String> ids = JsonPath.read(body, "$.content[*].id");
        assertThat(ids).contains(pending.toString());
        assertThat(ids).doesNotContain(confirmed.toString(), cancelled.toString());
    }

    @Test
    void theManagerAndBoardWithReadAllCanConsultPendingItems() throws Exception {
        UUID req = seedRequest("PENDING", null, null, null, "TRAVEL_PACKAGE:PENDING");
        for (String token : List.of(login("comercial", "comercial123"), login("diretor", "diretor123"))) {
            List<String> ids = JsonPath.read(pendingBody(token), "$.content[*].id");
            assertThat(ids).contains(req.toString());
        }
    }

    @Test
    void aSellerWithoutABookingReadTierIsForbidden() throws Exception {
        seedRequest("PENDING", null, null, null, "TRAVEL_PACKAGE:PENDING");
        mvc.perform(get("/api/bookings/pending").header("Authorization", "Bearer " + login("vendedor", "vendedor123")))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnauthenticated() throws Exception {
        mvc.perform(get("/api/bookings/pending")).andExpect(status().isUnauthorized());
    }

    // --- helpers ---

    private Map<String, Object> pendingItem(String token, UUID requestId) throws Exception {
        List<Map<String, Object>> matches =
                JsonPath.read(pendingBody(token), "$.content[?(@.id=='" + requestId + "')]");
        assertThat(matches).as("request %s should be pending", requestId).hasSize(1);
        return matches.get(0);
    }

    private String pendingBody(String token) throws Exception {
        return mvc.perform(get("/api/bookings/pending").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    /** Seeds a Booking Request in a given state (status, operator, lastAttemptAt, nextActionDate) with items
     * described as {@code TYPE:STATUS} (requiresBooking = type != SERVICE_FEE). */
    private UUID seedRequest(
            String status, UUID operatorId, String lastAttemptAtIso, String nextActionDateIso, String... itemSpecs) {
        UUID lead = insertLead();
        UUID opp = insertOpportunity(lead);
        UUID proposal = insertProposal(opp, lead);
        UUID order = insertOrder(proposal, opp, lead);
        UUID requestId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO booking_requests (id, version, commercial_order_id, proposal_id, opportunity_id,
                                              lead_id, responsible_person_id, booking_operator_id, status,
                                              last_attempt_at, next_action_date,
                                              created_at, updated_at, created_by, updated_by)
                VALUES (cast(? as uuid), 0, cast(? as uuid), cast(? as uuid), cast(? as uuid), cast(? as uuid),
                        cast(? as uuid), cast(? as uuid), ?, cast(? as timestamptz), cast(? as date),
                        now(), now(), cast(? as uuid), cast(? as uuid))
                """,
                requestId.toString(),
                order.toString(),
                proposal.toString(),
                opp.toString(),
                lead.toString(),
                MANAGER.toString(),
                operatorId == null ? null : operatorId.toString(),
                status,
                lastAttemptAtIso,
                nextActionDateIso,
                MANAGER.toString(),
                MANAGER.toString());
        for (String spec : itemSpecs) {
            String[] parts = spec.split(":");
            String type = parts[0];
            String itemStatus = parts[1];
            boolean requires = !"SERVICE_FEE".equals(type);
            jdbc.update(
                    """
                    INSERT INTO booking_items (id, booking_request_id, order_item_id, type, description,
                                               quantity, requires_booking, status, created_at)
                    VALUES (cast(? as uuid), cast(? as uuid), cast(? as uuid), ?, ?, 1, ?, ?, now())
                    """,
                    UUID.randomUUID().toString(),
                    requestId.toString(),
                    UUID.randomUUID().toString(),
                    type,
                    "Item " + type,
                    requires,
                    itemStatus);
        }
        return requestId;
    }

    private UUID insertOrder(UUID proposalId, UUID opportunityId, UUID leadId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO commercial_orders (id, version, number, proposal_id, opportunity_id, lead_id,
                                               responsible_person_id, status, subtotal, total,
                                               created_by, updated_by)
                VALUES (cast(? as uuid), 0, nextval('commercial_order_number_seq'), cast(? as uuid),
                        cast(? as uuid), cast(? as uuid), cast(? as uuid), 'PENDING_BOOKING', 100.00, 100.00,
                        cast(? as uuid), cast(? as uuid))
                """,
                id.toString(),
                proposalId.toString(),
                opportunityId.toString(),
                leadId.toString(),
                MANAGER.toString(),
                MANAGER.toString(),
                MANAGER.toString());
        return id;
    }

    private UUID insertProposal(UUID opportunityId, UUID leadId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO proposals (id, version, opportunity_id, lead_id, responsible_person_id, title,
                                       status, subtotal, total, created_by, updated_by)
                VALUES (cast(? as uuid), 0, cast(? as uuid), cast(? as uuid), cast(? as uuid), 'Reserva',
                        'ACCEPTED', 100.00, 100.00, cast(? as uuid), cast(? as uuid))
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
                OpportunityStage.WON.name(),
                MANAGER.toString(),
                MANAGER.toString());
        return id;
    }

    private UUID insertLead() {
        UUID id = UUID.randomUUID();
        String phone = "1190000%04d".formatted(phoneSeq++);
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

    private String operator() throws Exception {
        return login("operacoes", "operacoes123");
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
