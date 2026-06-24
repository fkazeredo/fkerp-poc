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
 * End-to-end (MockMvc, real Postgres) that the consolidated Booking Request status is reflected onto the source
 * Commercial Order: creating the request reflects PENDING, an attempt reflects IN_PROGRESS, confirming some/all
 * items reflects PARTIALLY_CONFIRMED/CONFIRMED, and a failure reflects FAILED. The reflection is Sales-owned (a
 * synchronous event reaction): the Commercial Order's own lifecycle ({@code status}) is never changed by Booking
 * — a confirmed booking keeps the Order PENDING_BOOKING (identifiable as ready for Financial Operations) and a
 * failed booking does not cancel the Order. No Receivable, Payment or Commission data is created.
 */
class OrderBookingStatusReflectionApiIntegrationTest extends AbstractIntegrationTest {

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
    void creatingARequestReflectsPendingOntoTheOrder() throws Exception {
        UUID order = pendingBookingOrder("Pend", "TRAVEL_PACKAGE", "CAR_RENTAL");
        String token = operator();

        // No Booking Request yet → the Order has no reflected booking status.
        assertThat(bookingStatusOf(order, token)).isNull();

        createRequest(order, token);

        assertThat(bookingStatusOf(order, token)).isEqualTo("PENDING");
        // The Order's own lifecycle is untouched (Sales-owned).
        assertThat(lifecycleStatusOf(order, token)).isEqualTo("PENDING_BOOKING");
    }

    @Test
    void registeringAnAttemptReflectsInProgress() throws Exception {
        UUID order = pendingBookingOrder("Att", "TRAVEL_PACKAGE");
        String token = operator();
        UUID request = createRequest(order, token);

        mvc.perform(post("/api/bookings/" + request + "/attempts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"typeId\":\"%s\",\"resultId\":\"%s\",\"description\":\"Checando\",\"occurredAt\":\"2026-06-10T10:00:00Z\"}"
                                        .formatted(
                                                refId("booking_attempt_types", "INTERNAL_VERIFICATION"),
                                                refId("booking_attempt_results", "STARTED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        assertThat(bookingStatusOf(order, token)).isEqualTo("IN_PROGRESS");
    }

    @Test
    void confirmingAllItemsReflectsConfirmedAndKeepsTheOrderReadyForFinance() throws Exception {
        UUID order = pendingBookingOrder("Conf", "TRAVEL_PACKAGE", "CAR_RENTAL");
        String token = operator();
        UUID request = createRequest(order, token);

        // Confirm the travel package → one of two requiring confirmed → PARTIALLY_CONFIRMED.
        mvc.perform(
                        post("/api/bookings/" + request + "/items/" + bookingItemId(request, "TRAVEL_PACKAGE")
                                        + "/confirm")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"externalSystem\":\"Amadeus\",\"externalLocator\":\"ABC123\",\"confirmedAt\":\"2026-06-10T10:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_CONFIRMED"));
        assertThat(bookingStatusOf(order, token)).isEqualTo("PARTIALLY_CONFIRMED");

        // Confirm the car rental → all requiring confirmed → CONFIRMED.
        mvc.perform(
                        post("/api/bookings/" + request + "/items/" + bookingItemId(request, "CAR_RENTAL")
                                        + "/confirm-car-rental")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"externalSystem\":\"Localiza\",\"externalLocator\":\"CAR-77\",\"confirmedAt\":\"2026-06-10T11:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // The Order is identifiable as ready for Financial Operations: booking CONFIRMED, lifecycle untouched.
        assertThat(bookingStatusOf(order, token)).isEqualTo("CONFIRMED");
        assertThat(lifecycleStatusOf(order, token)).isEqualTo("PENDING_BOOKING");
    }

    @Test
    void failingReflectsFailedAndDoesNotCancelTheOrder() throws Exception {
        UUID order = pendingBookingOrder("Fail", "TRAVEL_PACKAGE");
        String token = operator();
        UUID request = createRequest(order, token);

        mvc.perform(post("/api/bookings/" + request + "/items/" + bookingItemId(request, "TRAVEL_PACKAGE") + "/fail")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"failureReasonId\":\"%s\",\"failedAt\":\"2026-06-10T10:00:00Z\"}"
                                .formatted(refId("booking_failure_reasons", "NO_AVAILABILITY"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        // The Order is identifiable as having a booking problem, but is NOT cancelled (Booking takes no ownership).
        assertThat(bookingStatusOf(order, token)).isEqualTo("FAILED");
        assertThat(lifecycleStatusOf(order, token)).isEqualTo("PENDING_BOOKING");
        assertThat(lifecycleStatusOf(order, token)).isNotEqualTo("CANCELLED");
    }

    @Test
    void retryConfirmAfterFailureReconsolidatesTheReflection() throws Exception {
        UUID order = pendingBookingOrder("Retry", "TRAVEL_PACKAGE");
        String token = operator();
        UUID request = createRequest(order, token);
        UUID item = bookingItemId(request, "TRAVEL_PACKAGE");

        mvc.perform(post("/api/bookings/" + request + "/items/" + item + "/fail")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"failureReasonId\":\"%s\",\"failedAt\":\"2026-06-10T10:00:00Z\"}"
                                .formatted(refId("booking_failure_reasons", "NO_AVAILABILITY"))))
                .andExpect(status().isOk());
        assertThat(bookingStatusOf(order, token)).isEqualTo("FAILED");

        // Retry succeeds → the reflection recovers to CONFIRMED.
        mvc.perform(
                        post("/api/bookings/" + request + "/items/" + item + "/confirm")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"externalSystem\":\"Amadeus\",\"externalLocator\":\"ABC123\",\"confirmedAt\":\"2026-06-11T10:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(bookingStatusOf(order, token)).isEqualTo("CONFIRMED");
    }

    @Test
    void theOrderDetailExposesBookingStatusAndNoFinancialData() throws Exception {
        UUID order = pendingBookingOrder("Keys", "TRAVEL_PACKAGE");
        String token = operator();
        createRequest(order, token);

        String body = mvc.perform(get("/api/orders/" + order).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingStatus").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // The contract exposes commercial-order data + the booking reflection only — never Receivable, Payment,
        // Commission, Sale or Customer Care data.
        Map<String, Object> detail = JsonPath.read(body, "$");
        assertThat(detail.keySet())
                .containsExactlyInAnyOrder(
                        "id",
                        "number",
                        "proposalId",
                        "opportunityId",
                        "leadId",
                        "status",
                        "requiresBooking",
                        "bookingStatus",
                        "financialStatus",
                        "responsibleId",
                        "responsibleName",
                        "unassigned",
                        "items",
                        "subtotal",
                        "total",
                        "createdAt",
                        "createdByName",
                        "sourceProposal",
                        "sourceOpportunity",
                        "sourceLead");
    }

    private UUID refId(String table, String code) {
        return UUID.fromString(
                jdbc.queryForObject("SELECT id::text FROM " + table + " WHERE code = ?", String.class, code));
    }

    private UUID createRequest(UUID order, String token) throws Exception {
        String created = mvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\"}".formatted(order)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(JsonPath.read(created, "$.id"));
    }

    private String bookingStatusOf(UUID order, String token) throws Exception {
        String body = mvc.perform(get("/api/orders/" + order).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.bookingStatus");
    }

    private String lifecycleStatusOf(UUID order, String token) throws Exception {
        String body = mvc.perform(get("/api/orders/" + order).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.status");
    }

    private UUID bookingItemId(UUID requestId, String type) {
        return jdbc.queryForObject(
                "SELECT bi.id FROM booking_items bi JOIN proposal_item_types t ON t.id = bi.type_id "
                        + "WHERE bi.booking_request_id = ?::uuid AND t.code = ? LIMIT 1",
                UUID.class,
                requestId.toString(),
                type);
    }

    /** Seeds a PENDING_BOOKING Order with the given item types (responsible = the manager). */
    private UUID pendingBookingOrder(String name, String... itemTypes) {
        UUID lead = insertLead(name);
        UUID opportunity = insertOpportunity(name, lead);
        UUID proposal = insertProposal(name, opportunity, lead);
        UUID orderId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO commercial_orders (id, version, number, proposal_id, opportunity_id, lead_id,
                                               responsible_person_id, status, subtotal, total,
                                               created_by, updated_by)
                VALUES (cast(? as uuid), 0, nextval('commercial_order_number_seq'), cast(? as uuid),
                        cast(? as uuid), cast(? as uuid), cast(? as uuid), 'PENDING_BOOKING', 100.00, 100.00,
                        cast(? as uuid), cast(? as uuid))
                """,
                orderId.toString(),
                proposal.toString(),
                opportunity.toString(),
                lead.toString(),
                MANAGER.toString(),
                MANAGER.toString(),
                MANAGER.toString());
        for (String type : itemTypes) {
            jdbc.update(
                    """
                    INSERT INTO commercial_order_items (id, order_id, type, description, quantity, unit_value)
                    VALUES (cast(? as uuid), cast(? as uuid), ?, ?, 1, 100.00)
                    """,
                    UUID.randomUUID().toString(),
                    orderId.toString(),
                    type,
                    "Item " + type);
        }
        return orderId;
    }

    private UUID insertProposal(String name, UUID opportunityId, UUID leadId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO proposals (id, version, opportunity_id, lead_id, responsible_person_id, title,
                                       status, subtotal, total, created_by, updated_by)
                VALUES (cast(? as uuid), 0, cast(? as uuid), cast(? as uuid), cast(? as uuid), ?,
                        'ACCEPTED', 100.00, 100.00, cast(? as uuid), cast(? as uuid))
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
                        ?, ?, NULL, cast(? as uuid), cast(? as uuid))
                """,
                id.toString(),
                leadId.toString(),
                name,
                originId.toString(),
                MANAGER.toString(),
                "Pacote " + name,
                "WON",
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
