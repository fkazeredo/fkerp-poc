package com.fksoft.erp.application.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fksoft.erp.AbstractIntegrationTest;
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
 * End-to-end (MockMvc, real Postgres) of the Booking Operations indicators {@code GET /api/bookings/indicators}:
 * the volume figures (total, by status, items by type, failed items, average creation→confirmation time) over the
 * period, plus the current ready-for-Finance snapshot (CONFIRMED). Gated by the Booking read tiers (the policy
 * narrows visibility): read-all profiles see the global figures; profiles without a booking read tier get 403.
 * The contract carries operational reservation figures only — never Financial/Payment/Commission data.
 */
class BookingIndicatorsApiIntegrationTest extends AbstractIntegrationTest {

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
    void reportsTotalsByStatusItemsByTypeAndFailedItems() throws Exception {
        seedRequest("PENDING", "2026-06-10T10:00:00Z", null, "TRAVEL_PACKAGE:PENDING");
        seedRequest("IN_PROGRESS", "2026-06-11T10:00:00Z", null, "CAR_RENTAL:PENDING");
        seedRequest("FAILED", "2026-06-12T10:00:00Z", null, "TRAVEL_PACKAGE:FAILED", "SERVICE_FEE:NOT_REQUIRED");

        String body = indicators(operator(), null, null);
        assertThat(JsonPath.<Integer>read(body, "$.total")).isEqualTo(3);
        assertThat(statusCount(body, "PENDING")).isEqualTo(1);
        assertThat(statusCount(body, "IN_PROGRESS")).isEqualTo(1);
        assertThat(statusCount(body, "FAILED")).isEqualTo(1);
        assertThat(itemTypeCount(body, "TRAVEL_PACKAGE")).isEqualTo(2);
        assertThat(itemTypeCount(body, "CAR_RENTAL")).isEqualTo(1);
        assertThat(itemTypeCount(body, "SERVICE_FEE")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(body, "$.failedItems")).isEqualTo(1);

        // The contract exposes operational figures only — never Financial/Payment/Commission.
        Map<String, Object> json = JsonPath.read(body, "$");
        assertThat(json.keySet())
                .containsExactlyInAnyOrder(
                        "total", "byStatus", "itemsByType", "failedItems", "readyForFinance", "avgConfirmationSeconds");
    }

    @Test
    void readyForFinanceIsTheCurrentConfirmedCountIndependentOfThePeriod() throws Exception {
        // Two confirmed (in different months) + one pending.
        seedRequest("CONFIRMED", "2026-05-02T10:00:00Z", "2026-05-02T12:00:00Z", "TRAVEL_PACKAGE:CONFIRMED");
        seedRequest("CONFIRMED", "2026-06-02T10:00:00Z", "2026-06-02T12:00:00Z", "CAR_RENTAL:CONFIRMED");
        seedRequest("PENDING", "2026-06-03T10:00:00Z", null, "TRAVEL_PACKAGE:PENDING");

        // A June-only period sees 1 confirmed in the volume, but ready-for-Finance counts all current CONFIRMED.
        String june = indicators(operator(), "2026-06-01", "2026-06-30");
        assertThat(statusCount(june, "CONFIRMED")).isEqualTo(1); // volume in period
        assertThat(JsonPath.<Integer>read(june, "$.readyForFinance")).isEqualTo(2); // snapshot, all confirmed
    }

    @Test
    void averageConfirmationTimeIsComputedOverConfirmedRequestsInThePeriod() throws Exception {
        // Two confirmed in June: 2h (7200s) and 4h (14400s) from creation → average 10800s.
        seedRequest("CONFIRMED", "2026-06-10T10:00:00Z", "2026-06-10T12:00:00Z", "TRAVEL_PACKAGE:CONFIRMED");
        seedRequest("CONFIRMED", "2026-06-11T10:00:00Z", "2026-06-11T14:00:00Z", "CAR_RENTAL:CONFIRMED");

        String june = indicators(operator(), "2026-06-01", "2026-06-30");
        assertThat(JsonPath.<Integer>read(june, "$.avgConfirmationSeconds")).isEqualTo(10800);

        // A period with no confirmed requests → null.
        String july = indicators(operator(), "2026-07-01", "2026-07-31");
        assertThat(JsonPath.<Object>read(july, "$.avgConfirmationSeconds")).isNull();
    }

    @Test
    void theCommercialManagerAndBoardCanConsultTheIndicators() throws Exception {
        seedRequest("PENDING", "2026-06-10T10:00:00Z", null, "TRAVEL_PACKAGE:PENDING");
        for (String token : List.of(login("comercial", "comercial123"), login("diretor", "diretor123"))) {
            assertThat(JsonPath.<Integer>read(indicators(token, null, null), "$.total"))
                    .isEqualTo(1);
        }
    }

    @Test
    void aSellerWithoutABookingReadTierIsForbidden() throws Exception {
        mvc.perform(get("/api/bookings/indicators")
                        .header("Authorization", "Bearer " + login("vendedor", "vendedor123")))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnauthenticated() throws Exception {
        mvc.perform(get("/api/bookings/indicators")).andExpect(status().isUnauthorized());
    }

    // --- helpers ---

    private int statusCount(String body, String status) {
        List<Integer> counts = JsonPath.read(body, "$.byStatus[?(@.status=='" + status + "')].count");
        return counts.isEmpty() ? 0 : counts.get(0);
    }

    private int itemTypeCount(String body, String type) {
        List<Integer> counts = JsonPath.read(body, "$.itemsByType[?(@.type=='" + type + "')].count");
        return counts.isEmpty() ? 0 : counts.get(0);
    }

    private String indicators(String token, String from, String to) throws Exception {
        var request = get("/api/bookings/indicators").header("Authorization", "Bearer " + token);
        if (from != null) {
            request = request.param("createdFrom", from);
        }
        if (to != null) {
            request = request.param("createdTo", to);
        }
        return mvc.perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    /** Seeds a Booking Request with an explicit status / created_at / confirmed_at and items ({@code TYPE:STATUS}). */
    private UUID seedRequest(String status, String createdAtIso, String confirmedAtIso, String... itemSpecs) {
        UUID lead = insertLead();
        UUID opp = insertOpportunity(lead);
        UUID proposal = insertProposal(opp, lead);
        UUID order = insertOrder(proposal, opp, lead);
        UUID requestId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO booking_requests (id, version, commercial_order_id, proposal_id, opportunity_id,
                                              lead_id, responsible_person_id, booking_operator_id, status,
                                              confirmed_at, created_at, updated_at, created_by, updated_by)
                VALUES (cast(? as uuid), 0, cast(? as uuid), cast(? as uuid), cast(? as uuid), cast(? as uuid),
                        cast(? as uuid), cast(? as uuid), ?, cast(? as timestamptz), cast(? as timestamptz),
                        now(), cast(? as uuid), cast(? as uuid))
                """,
                requestId.toString(),
                order.toString(),
                proposal.toString(),
                opp.toString(),
                lead.toString(),
                MANAGER.toString(),
                OPERATOR.toString(),
                status,
                confirmedAtIso,
                createdAtIso,
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
                "WON",
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
