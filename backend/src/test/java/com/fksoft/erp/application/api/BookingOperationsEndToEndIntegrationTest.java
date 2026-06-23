package com.fksoft.erp.application.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * Sprint 4 end-to-end validation: the Booking Operations flow as one coherent journey through the real API,
 * starting from a Commercial Order created by the Sprint 3 flow (proposal → accept → order), not as isolated
 * operations. Covers the three flows from the slice — the main confirmation flow, the failure + retry flow and
 * the partially-confirmed flow — and that the Commercial Order reflects the consolidated booking status while
 * staying owned by Sales, that a confirmed booking carries enough information for Sprint 5 Financial Operations,
 * that no Financial/Payment/Commission data appears in the contracts, and that visibility holds during the flow.
 */
class BookingOperationsEndToEndIntegrationTest extends AbstractIntegrationTest {

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
    private int seq;

    @BeforeEach
    void seed() {
        wipe();
        originId = origins.findByActiveTrueOrderBySortOrderAsc().get(0).id();
        seq = 0;
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
    void mainConfirmationFlowFromASprint3OrderToConfirmedReflectedOntoTheOrder() throws Exception {
        // 1. A Sprint 3 Commercial Order, Pending Booking, with a travel package + a car rental + a service fee.
        UUID order = orderFromSprint3("TRAVEL_PACKAGE", "CAR_RENTAL", "SERVICE_FEE");
        assertThat(orderField(order, "$.status")).isEqualTo("PENDING_BOOKING");
        assertThat(orderField(order, "$.bookingStatus")).isNull(); // no Booking Request yet

        String op = operator();

        // 2-5. Create the Booking Request → PENDING, items classified by booking need.
        UUID request = createBooking(order, op);
        String detail = bookingDetail(request, op);
        assertThat(JsonPath.<String>read(detail, "$.status")).isEqualTo("PENDING");
        assertThat(itemStatus(detail, "TRAVEL_PACKAGE")).isEqualTo("PENDING"); // requires booking
        assertThat(itemStatus(detail, "CAR_RENTAL")).isEqualTo("PENDING"); // requires booking
        assertThat(itemStatus(detail, "SERVICE_FEE")).isEqualTo("NOT_REQUIRED"); // never requires booking
        assertThat(requires(detail, "TRAVEL_PACKAGE")).isEqualTo(true);
        assertThat(requires(detail, "SERVICE_FEE")).isEqualTo(false);
        assertThat(orderField(order, "$.bookingStatus")).isEqualTo("PENDING"); // reflected on the Order

        // 6-7. A manual attempt moves the request to In Progress (and the Order reflects it).
        attempt(request, op);
        assertThat(orderField(order, "$.bookingStatus")).isEqualTo("IN_PROGRESS");

        // 8. Confirm the travel package (external locator) → partially confirmed.
        confirmTravelPackage(request, itemId(detail, "TRAVEL_PACKAGE"), op)
                .andExpect(jsonPath("$.status").value("PARTIALLY_CONFIRMED"));
        assertThat(orderField(order, "$.bookingStatus")).isEqualTo("PARTIALLY_CONFIRMED");

        // 9-10. Confirm the car rental (external locator) → the whole request is Confirmed.
        confirmCarRental(request, itemId(detail, "CAR_RENTAL"), op)
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // 11. The Commercial Order reflects CONFIRMED (ready for Financial Operations) — but Sales still owns its
        // lifecycle (the Order's own status is untouched and it is not cancelled).
        assertThat(orderField(order, "$.bookingStatus")).isEqualTo("CONFIRMED");
        assertThat(orderField(order, "$.status")).isEqualTo("PENDING_BOOKING");

        // Enough information for Sprint 5: the confirmed booking keeps the source Order/Proposal/Opportunity/Lead
        // traceable, the items carry their external locators, and the Order carries the commercial total.
        String confirmed = bookingDetail(request, op);
        assertThat(JsonPath.<String>read(confirmed, "$.sourceOrder.id")).isEqualTo(order.toString());
        assertThat(JsonPath.<String>read(confirmed, "$.sourceProposal.id")).isNotNull();
        assertThat(JsonPath.<String>read(confirmed, "$.sourceOpportunity.id")).isNotNull();
        assertThat(JsonPath.<String>read(confirmed, "$.sourceLead.id")).isNotNull();
        List<String> locators = JsonPath.read(confirmed, "$.items[?(@.confirmation)].confirmation.externalLocator");
        assertThat(locators).contains("ABC123", "CAR-77");
        assertThat(JsonPath.<Number>read(orderBody(order), "$.total").doubleValue())
                .isGreaterThan(0d);

        // 12-14. No Receivable/Payment/Commission and no external integration: the contracts carry operational /
        // commercial data only (no financial fields leak into the booking or order detail).
        assertThat(JsonPath.<Map<String, Object>>read(confirmed, "$").keySet())
                .doesNotContain("receivable", "payment", "commission", "invoice");
        assertThat(JsonPath.<Map<String, Object>>read(orderBody(order), "$").keySet())
                .doesNotContain("receivable", "payment", "commission", "invoice");
    }

    @Test
    void failureThenRetryFlow() throws Exception {
        UUID order = orderFromSprint3("TRAVEL_PACKAGE", "CAR_RENTAL");
        String op = operator();
        UUID request = createBooking(order, op);
        String detail = bookingDetail(request, op);
        UUID car = itemId(detail, "CAR_RENTAL");

        // Attempt → In Progress.
        attempt(request, op);

        // Fail the car rental → the request becomes Failed and the item is an operational problem.
        mvc.perform(post(url(request, "items/" + car + "/fail"))
                        .header("Authorization", "Bearer " + op)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"failureReasonId\":\"%s\",\"failedAt\":\"2026-06-10T10:00:00Z\"}"
                                .formatted(refId("booking_failure_reasons", "NO_AVAILABILITY"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
        String afterFail = bookingDetail(request, op);
        assertThat(itemStatus(afterFail, "CAR_RENTAL")).isEqualTo("FAILED");
        assertThat(JsonPath.<List<String>>read(afterFail, "$.items[?(@.type=='CAR_RENTAL')].failure.failureReason"))
                .containsExactly("Sem disponibilidade");
        assertThat(orderField(order, "$.bookingStatus")).isEqualTo("FAILED");

        // A new manual attempt on the failed item is history only — the request stays Failed.
        mvc.perform(post(url(request, "attempts"))
                        .header("Authorization", "Bearer " + op)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"bookingItemId\":\"%s\",\"typeId\":\"%s\",\"resultId\":\"%s\",\"description\":\"Outro fornecedor\",\"occurredAt\":\"2026-06-10T11:00:00Z\"}"
                                        .formatted(
                                                car,
                                                refId("booking_attempt_types", "SUPPLIER_PHONE_CONTACT"),
                                                refId("booking_attempt_results", "NEEDS_RETRY"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        // Retry succeeds: confirm the previously failed car rental → reconsolidates to Partially Confirmed (the
        // travel package is still pending); confirming it as well → Confirmed.
        confirmCarRental(request, car, op).andExpect(jsonPath("$.status").value("PARTIALLY_CONFIRMED"));
        assertThat(orderField(order, "$.bookingStatus")).isEqualTo("PARTIALLY_CONFIRMED");
        confirmTravelPackage(request, itemId(detail, "TRAVEL_PACKAGE"), op)
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(orderField(order, "$.bookingStatus")).isEqualTo("CONFIRMED");
    }

    @Test
    void partiallyConfirmedFlowReflectsOntoTheOrderWithoutFinancialBehavior() throws Exception {
        UUID order = orderFromSprint3("TRAVEL_PACKAGE", "CAR_RENTAL");
        String op = operator();
        UUID request = createBooking(order, op);
        String detail = bookingDetail(request, op);

        // Confirm only one of the two requiring items → Partially Confirmed, reflected on the Order; the car
        // rental stays pending. The Order's own lifecycle is untouched and no financial data is produced.
        confirmTravelPackage(request, itemId(detail, "TRAVEL_PACKAGE"), op)
                .andExpect(jsonPath("$.status").value("PARTIALLY_CONFIRMED"))
                .andExpect(jsonPath("$.itemsConfirmed").value(1));
        String after = bookingDetail(request, op);
        assertThat(itemStatus(after, "CAR_RENTAL")).isEqualTo("PENDING");
        assertThat(orderField(order, "$.bookingStatus")).isEqualTo("PARTIALLY_CONFIRMED");
        assertThat(orderField(order, "$.status")).isEqualTo("PENDING_BOOKING");
        assertThat(JsonPath.<Map<String, Object>>read(orderBody(order), "$").keySet())
                .doesNotContain("receivable", "payment", "commission");
    }

    @Test
    void visibilityHoldsDuringTheFlowForAUserWithoutABookingReadTier() throws Exception {
        UUID order = orderFromSprint3("TRAVEL_PACKAGE", "CAR_RENTAL");
        UUID request = createBooking(order, operator());
        // A seller has no booking read tier → cannot see the reservation, the pending worklist or the indicators.
        String seller = login("vendedor", "vendedor123");
        mvc.perform(get(url(request, "")).header("Authorization", "Bearer " + seller))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/bookings/pending").header("Authorization", "Bearer " + seller))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/bookings/indicators").header("Authorization", "Bearer " + seller))
                .andExpect(status().isForbidden());
    }

    // --- API helpers (the flow is driven through the real endpoints) ---

    private String url(UUID request, String suffix) {
        return "/api/bookings/" + request + (suffix.isEmpty() ? "" : "/" + suffix);
    }

    private UUID createBooking(UUID order, String token) throws Exception {
        String created = mvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\"}".formatted(order)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(JsonPath.read(created, "$.id"));
    }

    private void attempt(UUID request, String token) throws Exception {
        mvc.perform(post(url(request, "attempts"))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"typeId\":\"%s\",\"resultId\":\"%s\",\"description\":\"Checando\",\"occurredAt\":\"2026-06-10T09:00:00Z\"}"
                                        .formatted(
                                                refId("booking_attempt_types", "INTERNAL_VERIFICATION"),
                                                refId("booking_attempt_results", "STARTED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    private UUID refId(String table, String code) {
        return UUID.fromString(
                jdbc.queryForObject("SELECT id::text FROM " + table + " WHERE code = ?", String.class, code));
    }

    private ResultActions confirmTravelPackage(UUID request, UUID item, String token) throws Exception {
        return mvc.perform(
                        post(url(request, "items/" + item + "/confirm"))
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"externalSystem\":\"Amadeus\",\"externalLocator\":\"ABC123\",\"confirmedAt\":\"2026-06-10T10:00:00Z\"}"))
                .andExpect(status().isOk());
    }

    private ResultActions confirmCarRental(UUID request, UUID item, String token) throws Exception {
        return mvc.perform(
                        post(url(request, "items/" + item + "/confirm-car-rental"))
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"externalSystem\":\"Localiza\",\"externalLocator\":\"CAR-77\",\"confirmedAt\":\"2026-06-10T11:00:00Z\"}"))
                .andExpect(status().isOk());
    }

    private String bookingDetail(UUID request, String token) throws Exception {
        return mvc.perform(get(url(request, "")).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String itemStatus(String detail, String type) {
        return JsonPath.<List<String>>read(detail, "$.items[?(@.type=='" + type + "')].status")
                .get(0);
    }

    private Object requires(String detail, String type) {
        return JsonPath.<List<Object>>read(detail, "$.items[?(@.type=='" + type + "')].requiresBooking")
                .get(0);
    }

    private UUID itemId(String detail, String type) {
        return UUID.fromString(JsonPath.<List<String>>read(detail, "$.items[?(@.type=='" + type + "')].id")
                .get(0));
    }

    private String orderBody(UUID order) throws Exception {
        return mvc.perform(get("/api/orders/" + order).header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String orderField(UUID order, String path) throws Exception {
        Object value = JsonPath.read(orderBody(order), path);
        return value == null ? null : value.toString();
    }

    // --- Sprint 3: build a PENDING_BOOKING Commercial Order through the real proposal → order API ---

    private UUID orderFromSprint3(String... itemTypes) throws Exception {
        String mgr = manager();
        UUID lead = insertLead();
        UUID opp = insertOpportunity(lead);
        UUID proposal = insertProposal(opp, lead);
        for (String type : itemTypes) {
            mvc.perform(post("/api/proposals/" + proposal + "/items")
                            .header("Authorization", "Bearer " + mgr)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"typeId\":\"%s\",\"description\":\"x\",\"quantity\":1,\"unitValue\":500.00}"
                                    .formatted(proposalItemTypeId(type))))
                    .andExpect(status().isOk());
        }
        mvc.perform(put("/api/proposals/" + proposal)
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"validUntil\":\"2026-12-31\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/proposals/" + proposal + "/submit").header("Authorization", "Bearer " + mgr))
                .andExpect(status().isOk());
        mvc.perform(post("/api/proposals/" + proposal + "/approve").header("Authorization", "Bearer " + mgr))
                .andExpect(status().isOk());
        mvc.perform(post("/api/proposals/" + proposal + "/send")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/proposals/" + proposal + "/accept")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        String created = mvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proposalId\":\"%s\"}".formatted(proposal)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(JsonPath.read(created, "$.id"));
    }

    private UUID insertProposal(UUID opportunityId, UUID leadId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO proposals (id, version, opportunity_id, lead_id, responsible_person_id, title,
                                       status, created_by, updated_by)
                VALUES (cast(? as uuid), 0, cast(? as uuid), cast(? as uuid), cast(? as uuid), 'Reserva', 'DRAFT',
                        cast(? as uuid), cast(? as uuid))
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
                "READY_FOR_PROPOSAL",
                MANAGER.toString(),
                MANAGER.toString());
        return id;
    }

    private UUID insertLead() {
        UUID id = UUID.randomUUID();
        String phone = "1190000%04d".formatted(seq++);
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
