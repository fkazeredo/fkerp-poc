package com.fksoft.erp.application.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fksoft.erp.AbstractIntegrationTest;
import com.fksoft.erp.domain.booking.model.BookingRequest;
import com.fksoft.erp.domain.booking.repository.BookingRequestRepository;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.repository.OpportunityRepository;
import com.fksoft.erp.domain.crm.repository.OriginRepository;
import com.fksoft.erp.domain.identity.AuthenticatedUser;
import com.fksoft.erp.domain.sales.repository.CommercialOrderRepository;
import com.fksoft.erp.domain.sales.repository.ProposalRepository;
import com.fksoft.erp.infra.security.TokenService;
import com.jayway.jsonpath.JsonPath;
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
 * End-to-end (MockMvc, real Postgres) of creating a Booking Request from a Commercial Order: the operations
 * user (006, holding {@code booking:request:create} + the Order read tier) creates the request, which starts
 * PENDING, preserves the source Order/Proposal/Opportunity/Lead references and the commercial responsible, and
 * snapshots the Order's items classified by booking need (a travel package → PENDING, a service fee →
 * NOT_REQUIRED). Only PENDING_BOOKING Orders can originate a request (else 422), an Order has at most one
 * active request (else 409), callers without the create authority get 403, a caller who cannot see the source
 * Order gets 403, and an unknown operator gets 422. Creating a request contacts no external system and creates
 * no Receivable/Payment/Commission data. The source Order (and its proposal/opportunity/lead) is seeded via JDBC.
 */
class BookingRequestApiIntegrationTest extends AbstractIntegrationTest {

    private static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private BookingRequestRepository bookingRequests;

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
    }

    @AfterEach
    void cleanup() {
        wipe();
    }

    private void wipe() {
        bookingRequests.deleteAll(); // FK to commercial_orders
        orders.deleteAll();
        proposals.deleteAll();
        opportunities.deleteAll();
        leads.deleteAll();
    }

    @Test
    void operationsUserCreatesABookingRequestPreservingRefsAndClassifyingItems() throws Exception {
        UUID order = pendingBookingOrder("Main");

        String created = mvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + login("operacoes", "operacoes123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\",\"notes\":\"Reservar com urgência\"}".formatted(order)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/bookings/")))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID requestId = UUID.fromString(JsonPath.read(created, "$.id"));

        // The saved aggregate preserves the source references and the commercial responsible, and starts PENDING.
        BookingRequest request = bookingRequests.findById(requestId).orElseThrow();
        assertThat(request.status()).isEqualTo("PENDING");
        assertThat(request.commercialOrderId()).isEqualTo(order);
        assertThat(request.proposalId()).isNotNull();
        assertThat(request.opportunityId()).isNotNull();
        assertThat(request.leadId()).isNotNull();
        assertThat(request.responsiblePersonId()).isEqualTo(MANAGER);
        assertThat(request.notes()).isEqualTo("Reservar com urgência");

        // The booking items snapshot the Order items, classified by booking need (no monetary columns exist).
        List<Map<String, Object>> items = jdbc.queryForList(
                "SELECT type, requires_booking, status FROM booking_items WHERE booking_request_id = ?::uuid",
                requestId.toString());
        assertThat(items).hasSize(2);
        Map<String, Object> pkg = items.stream()
                .filter(i -> "TRAVEL_PACKAGE".equals(i.get("type")))
                .findFirst()
                .orElseThrow();
        assertThat(pkg.get("requires_booking")).isEqualTo(true);
        assertThat(pkg.get("status")).isEqualTo("PENDING");
        Map<String, Object> fee = items.stream()
                .filter(i -> "SERVICE_FEE".equals(i.get("type")))
                .findFirst()
                .orElseThrow();
        assertThat(fee.get("requires_booking")).isEqualTo(false);
        assertThat(fee.get("status")).isEqualTo("NOT_REQUIRED");
    }

    @Test
    void theCommercialManagerCanAlsoCreateABookingRequest() throws Exception {
        UUID order = pendingBookingOrder("ByManager");
        mvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + login("comercial", "comercial123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\"}".formatted(order)))
                .andExpect(status().isCreated());
    }

    @Test
    void cannotCreateFromAnOrderThatIsNotPendingBooking() throws Exception {
        UUID order = order("NoBooking", "BOOKING_NOT_REQUIRED", "SERVICE_FEE");
        mvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + login("operacoes", "operacoes123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\"}".formatted(order)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("booking.order-not-pending"));
    }

    @Test
    void cannotCreateADuplicateActiveBookingRequestForTheSameOrder() throws Exception {
        UUID order = pendingBookingOrder("Dup");
        String token = login("operacoes", "operacoes123");
        mvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\"}".formatted(order)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\"}".formatted(order)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("booking.already-exists"))
                .andExpect(jsonPath("$.fields[?(@.field=='bookingRequestId')]").exists());
    }

    @Test
    void aUserWithoutTheCreateScopeIsForbidden() throws Exception {
        UUID order = pendingBookingOrder("NoScope");
        // vendedor (002) has sales:order:* but no booking:request:create.
        mvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + login("vendedor", "vendedor123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\"}".formatted(order)))
                .andExpect(status().isForbidden());
    }

    @Test
    void aUserWhoCannotSeeTheSourceOrderIsForbidden() throws Exception {
        UUID order = pendingBookingOrder("Hidden");
        // Holds booking:request:create but no Order read tier → cannot see the manager-owned Order.
        String token = tokens.issueAccessToken(
                new AuthenticatedUser(UUID.randomUUID(), "op", Set.of("booking:request:create")));
        mvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\"}".formatted(order)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("order.access-denied"));
    }

    @Test
    void rejectsAnUnknownBookingOperator() throws Exception {
        UUID order = pendingBookingOrder("BadOperator");
        mvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + login("operacoes", "operacoes123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\",\"bookingOperatorId\":\"%s\"}"
                                .formatted(order, UUID.randomUUID())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("booking.operator-not-found"));
    }

    @Test
    void marksAnOtherItemAsRequiringBookingAtCreationLeavingTheRestUnchanged() throws Exception {
        UUID order = order("OtherMark", "PENDING_BOOKING", "TRAVEL_PACKAGE", "OTHER", "SERVICE_FEE");
        UUID otherItem = orderItemId(order, "OTHER");

        String created = mvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + login("operacoes", "operacoes123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\",\"bookingRequiredItemIds\":[\"%s\"]}"
                                .formatted(order, otherItem)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID requestId = UUID.fromString(JsonPath.read(created, "$.id"));

        List<Map<String, Object>> items = jdbc.queryForList(
                "SELECT type, requires_booking, status FROM booking_items WHERE booking_request_id = ?::uuid",
                requestId.toString());
        assertThat(items).hasSize(3);
        // The explicitly-marked OTHER item now requires booking and is PENDING.
        Map<String, Object> other = items.stream()
                .filter(i -> "OTHER".equals(i.get("type")))
                .findFirst()
                .orElseThrow();
        assertThat(other.get("requires_booking")).isEqualTo(true);
        assertThat(other.get("status")).isEqualTo("PENDING");
        // The travel package still requires booking; the service fee is still NOT_REQUIRED (rules are fixed).
        Map<String, Object> pkg = items.stream()
                .filter(i -> "TRAVEL_PACKAGE".equals(i.get("type")))
                .findFirst()
                .orElseThrow();
        assertThat(pkg.get("requires_booking")).isEqualTo(true);
        Map<String, Object> fee = items.stream()
                .filter(i -> "SERVICE_FEE".equals(i.get("type")))
                .findFirst()
                .orElseThrow();
        assertThat(fee.get("requires_booking")).isEqualTo(false);
        assertThat(fee.get("status")).isEqualTo("NOT_REQUIRED");
    }

    @Test
    void anUnmarkedOtherItemStaysNotRequired() throws Exception {
        UUID order = order("OtherDefault", "PENDING_BOOKING", "TRAVEL_PACKAGE", "OTHER");

        String created = mvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + login("operacoes", "operacoes123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\"}".formatted(order)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID requestId = UUID.fromString(JsonPath.read(created, "$.id"));

        Map<String, Object> other = jdbc.queryForList(
                        "SELECT requires_booking, status FROM booking_items WHERE booking_request_id = ?::uuid AND type = 'OTHER'",
                        requestId.toString())
                .get(0);
        assertThat(other.get("requires_booking")).isEqualTo(false);
        assertThat(other.get("status")).isEqualTo("NOT_REQUIRED");
    }

    @Test
    void cannotMarkAServiceFeeItem() throws Exception {
        UUID order = order("BadMark", "PENDING_BOOKING", "TRAVEL_PACKAGE", "SERVICE_FEE");
        UUID feeItem = orderItemId(order, "SERVICE_FEE");
        mvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + login("operacoes", "operacoes123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\",\"bookingRequiredItemIds\":[\"%s\"]}"
                                .formatted(order, feeItem)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("booking.item-not-markable"))
                .andExpect(jsonPath("$.fields[?(@.field=='itemId')]").exists());
    }

    @Test
    void cannotMarkAnItemThatIsNotInTheOrder() throws Exception {
        UUID order = pendingBookingOrder("Foreign");
        mvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + login("operacoes", "operacoes123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\",\"bookingRequiredItemIds\":[\"%s\"]}"
                                .formatted(order, UUID.randomUUID())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("booking.item-not-markable"));
    }

    @Test
    void rejectsUnauthenticated() throws Exception {
        mvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    private UUID orderItemId(UUID orderId, String type) {
        return jdbc.queryForObject(
                "SELECT id FROM commercial_order_items WHERE order_id = ?::uuid AND type = ? LIMIT 1",
                UUID.class,
                orderId.toString(),
                type);
    }

    /** Seeds a PENDING_BOOKING Order (a travel package + a service fee), responsible = the manager. */
    private UUID pendingBookingOrder(String name) {
        return order(name, "PENDING_BOOKING", "TRAVEL_PACKAGE", "SERVICE_FEE");
    }

    private UUID order(String name, String status, String... itemTypes) {
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
                        cast(? as uuid), cast(? as uuid), cast(? as uuid), ?, 100.00, 100.00,
                        cast(? as uuid), cast(? as uuid))
                """,
                orderId.toString(),
                proposal.toString(),
                opportunity.toString(),
                lead.toString(),
                MANAGER.toString(),
                status,
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
