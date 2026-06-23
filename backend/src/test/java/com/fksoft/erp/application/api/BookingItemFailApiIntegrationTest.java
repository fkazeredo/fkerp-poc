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
 * End-to-end (MockMvc, real Postgres) of marking a booking item as failed: the operations user records the
 * failure reason (+ optional note, date); the item becomes FAILED and the Booking Request status consolidates
 * (FAILED when nothing requiring booking is confirmed, PARTIALLY_CONFIRMED when some are). A failed item stays
 * visible (the has-failed list filter finds it), may receive a new attempt, and may later be confirmed (retry),
 * which reconsolidates the request. Only an item that requires booking and is not already resolved can fail; the
 * reason and date are required; the operation is gated by booking:request:update + visibility; no external call,
 * the Commercial Order is not cancelled, and no Financial/Commission data is created.
 */
class BookingItemFailApiIntegrationTest extends AbstractIntegrationTest {

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
        jdbc.update("DELETE FROM booking_attempts");
        jdbc.update("DELETE FROM booking_items");
        jdbc.update("DELETE FROM booking_requests");
        jdbc.update("DELETE FROM commercial_order_items");
        orders.deleteAll();
        proposals.deleteAll();
        opportunities.deleteAll();
        leads.deleteAll();
    }

    private String valid() {
        return "{\"failureReasonId\":\"%s\",\"failureNote\":\"Fornecedor sem vaga\",\"failedAt\":\"2026-06-10T10:00:00Z\"}"
                .formatted(refId("booking_failure_reasons", "NO_AVAILABILITY"));
    }

    private UUID refId(String table, String code) {
        return UUID.fromString(
                jdbc.queryForObject("SELECT id::text FROM " + table + " WHERE code = ?", String.class, code));
    }

    private String failUrl(UUID request, UUID item) {
        return "/api/bookings/" + request + "/items/" + item + "/fail";
    }

    private String confirmUrl(UUID request, UUID item) {
        return "/api/bookings/" + request + "/items/" + item + "/confirm";
    }

    @Test
    void marksAnItemFailedRecordingTheReasonAndRollingUpToFailed() throws Exception {
        // A travel package (requires booking) + a service fee (does not) → failing the package, with nothing
        // confirmed, makes the request FAILED.
        UUID request = pendingRequest("TRAVEL_PACKAGE", "SERVICE_FEE");
        UUID item = itemId(request, "TRAVEL_PACKAGE");

        String body = mvc.perform(post(failUrl(request, item))
                        .header("Authorization", "Bearer " + operator())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.itemsFailed").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<Map<String, Object>> pkg = JsonPath.read(body, "$.items[?(@.type=='TRAVEL_PACKAGE')]");
        assertThat(pkg).hasSize(1);
        assertThat(pkg.get(0).get("status")).isEqualTo("FAILED");
        @SuppressWarnings("unchecked")
        Map<String, Object> failure = (Map<String, Object>) pkg.get(0).get("failure");
        assertThat(failure.get("failureReason")).isEqualTo("Sem disponibilidade");
        assertThat(failure.get("failureNote")).isEqualTo("Fornecedor sem vaga");
        assertThat(failure.get("failedByName")).isEqualTo("operacoes");
        assertThat(failure.get("failedAt")).isNotNull();
        // The failure carries operational data only — no monetary/financial fields.
        assertThat(failure.keySet())
                .containsExactlyInAnyOrder("failureReason", "failureNote", "failedByName", "failedAt");
    }

    @Test
    void failingOneItemWhileAnotherIsConfirmedLeavesPartiallyConfirmed() throws Exception {
        // A travel package + a car rental, both require booking. Confirm the package, then fail the car →
        // PARTIALLY_CONFIRMED (one confirmed, one failed).
        UUID request = pendingRequest("TRAVEL_PACKAGE", "CAR_RENTAL");
        String token = operator();
        mvc.perform(
                        post(confirmUrl(request, itemId(request, "TRAVEL_PACKAGE")))
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"externalSystem\":\"Amadeus\",\"externalLocator\":\"ABC123\",\"confirmedAt\":\"2026-06-10T10:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_CONFIRMED"));
        mvc.perform(post(failUrl(request, itemId(request, "CAR_RENTAL")))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_CONFIRMED"))
                .andExpect(jsonPath("$.itemsConfirmed").value(1))
                .andExpect(jsonPath("$.itemsFailed").value(1));
    }

    @Test
    void requiresReasonAndDate() throws Exception {
        UUID request = pendingRequest("TRAVEL_PACKAGE");
        UUID item = itemId(request, "TRAVEL_PACKAGE");
        String token = operator();
        // Missing reason.
        mvc.perform(post(failUrl(request, item))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"failedAt\":\"2026-06-10T10:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fields[?(@.field=='failureReasonId')]").exists());
        UUID other = refId("booking_failure_reasons", "OTHER");
        // Missing date.
        mvc.perform(post(failUrl(request, item))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"failureReasonId\":\"%s\"}".formatted(other)))
                .andExpect(status().isBadRequest());
        // Future date.
        mvc.perform(post(failUrl(request, item))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"failureReasonId\":\"%s\",\"failedAt\":\"2099-01-01T10:00:00Z\"}".formatted(other)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[?(@.field=='failedAt')]").exists());
    }

    @Test
    void theFailureNoteIsOptional() throws Exception {
        UUID request = pendingRequest("TRAVEL_PACKAGE");
        UUID item = itemId(request, "TRAVEL_PACKAGE");
        String body = mvc.perform(post(failUrl(request, item))
                        .header("Authorization", "Bearer " + operator())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"failureReasonId\":\"%s\",\"failedAt\":\"2026-06-10T10:00:00Z\"}"
                                .formatted(refId("booking_failure_reasons", "PRICE_CHANGED"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<Map<String, Object>> pkg = JsonPath.read(body, "$.items[?(@.type=='TRAVEL_PACKAGE')]");
        @SuppressWarnings("unchecked")
        Map<String, Object> failure = (Map<String, Object>) pkg.get(0).get("failure");
        assertThat(failure.get("failureReason")).isEqualTo("Preço alterado");
        assertThat(failure.get("failureNote")).isNull();
    }

    @Test
    void cannotFailAServiceFeeItemThatDoesNotRequireBooking() throws Exception {
        UUID request = pendingRequest("TRAVEL_PACKAGE", "SERVICE_FEE");
        UUID feeItem = itemId(request, "SERVICE_FEE");
        mvc.perform(post(failUrl(request, feeItem))
                        .header("Authorization", "Bearer " + operator())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("booking.item-not-failable"));
    }

    @Test
    void cannotFailAnAlreadyConfirmedItem() throws Exception {
        UUID request = pendingRequest("TRAVEL_PACKAGE");
        UUID item = itemId(request, "TRAVEL_PACKAGE");
        String token = operator();
        mvc.perform(
                        post(confirmUrl(request, item))
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"externalSystem\":\"Amadeus\",\"externalLocator\":\"ABC123\",\"confirmedAt\":\"2026-06-10T10:00:00Z\"}"))
                .andExpect(status().isOk());
        mvc.perform(post(failUrl(request, item))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("booking.item-already-resolved"));
    }

    @Test
    void rejectsAnItemNotInTheRequest() throws Exception {
        UUID request = pendingRequest("TRAVEL_PACKAGE");
        mvc.perform(post(failUrl(request, UUID.randomUUID()))
                        .header("Authorization", "Bearer " + operator())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("booking.item-not-found"));
    }

    @Test
    void aFailedItemCanReceiveANewAttemptAndStaysFailed() throws Exception {
        UUID request = pendingRequest("TRAVEL_PACKAGE");
        UUID item = itemId(request, "TRAVEL_PACKAGE");
        String token = operator();
        mvc.perform(post(failUrl(request, item))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
        // Registering a new attempt on the failed item is allowed; it is history and the item stays FAILED.
        mvc.perform(post("/api/bookings/" + request + "/attempts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"bookingItemId\":\"%s\",\"typeId\":\"%s\",\"resultId\":\"%s\",\"description\":\"Tentando outro fornecedor\",\"occurredAt\":\"2026-06-10T11:00:00Z\"}"
                                        .formatted(
                                                item,
                                                refId("booking_attempt_types", "SUPPLIER_PHONE_CONTACT"),
                                                refId("booking_attempt_results", "NEEDS_RETRY"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.attempts.length()").value(1))
                .andExpect(jsonPath(
                        "$.items[?(@.type=='TRAVEL_PACKAGE')].status", org.hamcrest.Matchers.hasItem("FAILED")));
    }

    @Test
    void aFailedItemCanLaterBeConfirmedAndTheRequestReconsolidates() throws Exception {
        UUID request = pendingRequest("TRAVEL_PACKAGE");
        UUID item = itemId(request, "TRAVEL_PACKAGE");
        String token = operator();
        mvc.perform(post(failUrl(request, item))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
        // Retry success: confirm the previously failed travel package → item CONFIRMED, request CONFIRMED.
        mvc.perform(
                        post(confirmUrl(request, item))
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"externalSystem\":\"Amadeus\",\"externalLocator\":\"ABC123\",\"confirmedAt\":\"2026-06-11T10:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.itemsConfirmed").value(1))
                .andExpect(jsonPath("$.itemsFailed").value(0));
    }

    @Test
    void aFailedItemMakesTheRequestVisibleInTheHasFailedFilter() throws Exception {
        UUID request = pendingRequest("TRAVEL_PACKAGE");
        UUID item = itemId(request, "TRAVEL_PACKAGE");
        String token = operator();
        mvc.perform(post(failUrl(request, item))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/bookings?hasFailedItems=true").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", org.hamcrest.Matchers.hasItem(request.toString())));
    }

    @Test
    void directorWithoutTheUpdateScopeIsForbidden() throws Exception {
        UUID request = pendingRequest("TRAVEL_PACKAGE");
        UUID item = itemId(request, "TRAVEL_PACKAGE");
        mvc.perform(post(failUrl(request, item))
                        .header("Authorization", "Bearer " + login("diretor", "diretor123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid()))
                .andExpect(status().isForbidden());
    }

    @Test
    void sellerIsForbidden() throws Exception {
        UUID request = pendingRequest("TRAVEL_PACKAGE");
        UUID item = itemId(request, "TRAVEL_PACKAGE");
        mvc.perform(post(failUrl(request, item))
                        .header("Authorization", "Bearer " + login("vendedor", "vendedor123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid()))
                .andExpect(status().isForbidden());
    }

    @Test
    void aUserWhoCannotSeeTheRequestIsForbidden() throws Exception {
        UUID request = pendingRequest("TRAVEL_PACKAGE");
        UUID item = itemId(request, "TRAVEL_PACKAGE");
        String token = tokens.issueAccessToken(new AuthenticatedUser(
                UUID.randomUUID(), "other", Set.of("booking:request:update", "booking:request:read")));
        mvc.perform(post(failUrl(request, item))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("booking.access-denied"));
    }

    @Test
    void unknownRequestIsNotFound() throws Exception {
        mvc.perform(post(failUrl(UUID.randomUUID(), UUID.randomUUID()))
                        .header("Authorization", "Bearer " + operator())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("booking.not-found"));
    }

    @Test
    void rejectsUnauthenticated() throws Exception {
        mvc.perform(post(failUrl(UUID.randomUUID(), UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid()))
                .andExpect(status().isUnauthorized());
    }

    private UUID itemId(UUID requestId, String type) {
        return jdbc.queryForObject(
                "SELECT id FROM booking_items WHERE booking_request_id = ?::uuid AND type = ? LIMIT 1",
                UUID.class,
                requestId.toString(),
                type);
    }

    /** Seeds a PENDING booking request with the given item types (responsible = the manager, operator = 006). */
    private UUID pendingRequest(String... itemTypes) {
        UUID lead = insertLead();
        UUID opp = insertOpportunity(lead);
        UUID proposal = insertProposal(opp, lead);
        UUID order = insertOrder(proposal, opp, lead);
        UUID requestId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO booking_requests (id, version, commercial_order_id, proposal_id, opportunity_id,
                                              lead_id, responsible_person_id, booking_operator_id, status,
                                              created_at, updated_at, created_by, updated_by)
                VALUES (cast(? as uuid), 0, cast(? as uuid), cast(? as uuid), cast(? as uuid), cast(? as uuid),
                        cast(? as uuid), cast(? as uuid), 'PENDING', now(), now(), cast(? as uuid), cast(? as uuid))
                """,
                requestId.toString(),
                order.toString(),
                proposal.toString(),
                opp.toString(),
                lead.toString(),
                MANAGER.toString(),
                OPERATOR.toString(),
                MANAGER.toString(),
                MANAGER.toString());
        for (String type : itemTypes) {
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
                    requires ? "PENDING" : "NOT_REQUIRED");
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
