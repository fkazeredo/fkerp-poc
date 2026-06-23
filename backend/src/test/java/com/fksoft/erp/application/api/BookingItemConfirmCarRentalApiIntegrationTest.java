package com.fksoft.erp.application.api;

import static org.assertj.core.api.Assertions.assertThat;
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
 * End-to-end (MockMvc, real Postgres) of manually confirming a Car Rental booking item: the operations user
 * records the external car reservation result (system/supplier, locator, date + optional rental metadata); the
 * item becomes CONFIRMED and the Booking Request status consolidates (CONFIRMED when every item requiring
 * booking is confirmed, PARTIALLY_CONFIRMED otherwise). Only a Car Rental item that requires booking and is not
 * already resolved can be confirmed through this flow (else 422); the locator and system are required (else
 * 400); the operation is gated by booking:request:update + visibility; no external call and no
 * Financial/Commission data.
 */
class BookingItemConfirmCarRentalApiIntegrationTest extends AbstractIntegrationTest {

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

    private static final String VALID =
            """
            {"externalSystem":"Localiza Connect","externalLocator":"CAR-77","confirmedAt":"2026-06-10T10:00:00Z",
             "rentalCompany":"Localiza","pickupLocation":"GRU Aeroporto","dropoffLocation":"Centro",
             "pickupAt":"2026-07-01T12:00:00Z","dropoffAt":"2026-07-08T12:00:00Z","carCategory":"SUV",
             "operationalNotes":"Retirada no balcão"}
            """;

    private String carUrl(UUID request, UUID item) {
        return "/api/bookings/" + request + "/items/" + item + "/confirm-car-rental";
    }

    @Test
    void confirmsACarRentalItemRecordingTheResultAndRollingUpToConfirmed() throws Exception {
        // A car rental (requires booking) + a service fee (does not) → confirming the car confirms all items
        // that require booking → the request becomes CONFIRMED.
        UUID request = pendingRequest("CAR_RENTAL", "SERVICE_FEE");
        UUID item = itemId(request, "CAR_RENTAL");

        String body = mvc.perform(post(carUrl(request, item))
                        .header("Authorization", "Bearer " + operator())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.itemsConfirmed").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<Map<String, Object>> car = JsonPath.read(body, "$.items[?(@.type=='CAR_RENTAL')]");
        assertThat(car).hasSize(1);
        assertThat(car.get(0).get("status")).isEqualTo("CONFIRMED");
        @SuppressWarnings("unchecked")
        Map<String, Object> confirmation = (Map<String, Object>) car.get(0).get("confirmation");
        assertThat(confirmation.get("externalSystem")).isEqualTo("Localiza Connect");
        assertThat(confirmation.get("externalLocator")).isEqualTo("CAR-77");
        assertThat(confirmation.get("confirmedByName")).isEqualTo("operacoes");
        assertThat(confirmation.get("rentalCompany")).isEqualTo("Localiza");
        assertThat(confirmation.get("pickupLocation")).isEqualTo("GRU Aeroporto");
        assertThat(confirmation.get("carCategory")).isEqualTo("SUV");
        // The travel-package-specific fields stay null for a Car Rental confirmation.
        assertThat(confirmation.get("packageDescription")).isNull();
    }

    @Test
    void confirmingOneCarWithOtherRequiringItemsLeavesTheRequestPartiallyConfirmed() throws Exception {
        // A travel package + a car rental, both require booking → confirming only the car leaves the request
        // PARTIALLY_CONFIRMED.
        UUID request = pendingRequest("TRAVEL_PACKAGE", "CAR_RENTAL");
        UUID item = itemId(request, "CAR_RENTAL");

        mvc.perform(post(carUrl(request, item))
                        .header("Authorization", "Bearer " + operator())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_CONFIRMED"))
                .andExpect(jsonPath("$.itemsConfirmed").value(1));
    }

    @Test
    void confirmingBothTheCarAndThePackageRollsUpToConfirmed() throws Exception {
        UUID request = pendingRequest("TRAVEL_PACKAGE", "CAR_RENTAL");
        String token = operator();
        // Confirm the car rental.
        mvc.perform(post(carUrl(request, itemId(request, "CAR_RENTAL")))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_CONFIRMED"));
        // Confirm the travel package via its own endpoint → all requiring items confirmed → CONFIRMED.
        mvc.perform(
                        post("/api/bookings/" + request + "/items/" + itemId(request, "TRAVEL_PACKAGE") + "/confirm")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"externalSystem\":\"Amadeus\",\"externalLocator\":\"ABC123\",\"confirmedAt\":\"2026-06-10T10:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.itemsConfirmed").value(2));
    }

    @Test
    void requiresExternalSystemLocatorAndDate() throws Exception {
        UUID request = pendingRequest("CAR_RENTAL");
        UUID item = itemId(request, "CAR_RENTAL");
        String token = operator();
        // Missing locator.
        mvc.perform(post(carUrl(request, item))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalSystem\":\"Localiza\",\"confirmedAt\":\"2026-06-10T10:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fields[?(@.field=='externalLocator')]").exists());
        // Missing system.
        mvc.perform(post(carUrl(request, item))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalLocator\":\"CAR-77\",\"confirmedAt\":\"2026-06-10T10:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[?(@.field=='externalSystem')]").exists());
        // Missing date.
        mvc.perform(post(carUrl(request, item))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalSystem\":\"Localiza\",\"externalLocator\":\"CAR-77\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAFutureConfirmationDate() throws Exception {
        UUID request = pendingRequest("CAR_RENTAL");
        UUID item = itemId(request, "CAR_RENTAL");
        mvc.perform(
                        post(carUrl(request, item))
                                .header("Authorization", "Bearer " + operator())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"externalSystem\":\"Localiza\",\"externalLocator\":\"CAR-77\",\"confirmedAt\":\"2099-01-01T10:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[?(@.field=='confirmedAt')]").exists());
    }

    @Test
    void cannotConfirmATravelPackageItemThroughTheCarRentalFlow() throws Exception {
        UUID request = pendingRequest("TRAVEL_PACKAGE", "CAR_RENTAL");
        UUID pkgItem = itemId(request, "TRAVEL_PACKAGE");
        mvc.perform(post(carUrl(request, pkgItem))
                        .header("Authorization", "Bearer " + operator())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("booking.item-not-confirmable"));
    }

    @Test
    void cannotConfirmAServiceFeeItem() throws Exception {
        UUID request = pendingRequest("CAR_RENTAL", "SERVICE_FEE");
        UUID feeItem = itemId(request, "SERVICE_FEE");
        mvc.perform(post(carUrl(request, feeItem))
                        .header("Authorization", "Bearer " + operator())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("booking.item-not-confirmable"));
    }

    @Test
    void cannotConfirmAnAlreadyConfirmedItem() throws Exception {
        UUID request = pendingRequest("CAR_RENTAL");
        UUID item = itemId(request, "CAR_RENTAL");
        String token = operator();
        mvc.perform(post(carUrl(request, item))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID))
                .andExpect(status().isOk());
        mvc.perform(post(carUrl(request, item))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("booking.item-already-resolved"));
    }

    @Test
    void rejectsAnItemNotInTheRequest() throws Exception {
        UUID request = pendingRequest("CAR_RENTAL");
        mvc.perform(post(carUrl(request, UUID.randomUUID()))
                        .header("Authorization", "Bearer " + operator())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("booking.item-not-found"));
    }

    @Test
    void directorWithoutTheUpdateScopeIsForbidden() throws Exception {
        UUID request = pendingRequest("CAR_RENTAL");
        UUID item = itemId(request, "CAR_RENTAL");
        mvc.perform(post(carUrl(request, item))
                        .header("Authorization", "Bearer " + login("diretor", "diretor123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID))
                .andExpect(status().isForbidden());
    }

    @Test
    void sellerIsForbidden() throws Exception {
        UUID request = pendingRequest("CAR_RENTAL");
        UUID item = itemId(request, "CAR_RENTAL");
        mvc.perform(post(carUrl(request, item))
                        .header("Authorization", "Bearer " + login("vendedor", "vendedor123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID))
                .andExpect(status().isForbidden());
    }

    @Test
    void aUserWhoCannotSeeTheRequestIsForbidden() throws Exception {
        UUID request = pendingRequest("CAR_RENTAL");
        UUID item = itemId(request, "CAR_RENTAL");
        String token = tokens.issueAccessToken(new AuthenticatedUser(
                UUID.randomUUID(), "other", Set.of("booking:request:update", "booking:request:read")));
        mvc.perform(post(carUrl(request, item))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("booking.access-denied"));
    }

    @Test
    void unknownRequestIsNotFound() throws Exception {
        mvc.perform(post(carUrl(UUID.randomUUID(), UUID.randomUUID()))
                        .header("Authorization", "Bearer " + operator())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("booking.not-found"));
    }

    @Test
    void rejectsUnauthenticated() throws Exception {
        mvc.perform(post(carUrl(UUID.randomUUID(), UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID))
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
